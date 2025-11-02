package com.earnbysms.smsgateway.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Service for downloading and installing APK updates
 * Handles secure APK downloads with progress tracking and verification
 */
class AppUpdateDownloader(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateDownloader"
        private const val MAIN_APP_APK_NAME = "app-mainapp-release.apk"
        private const val MAIN_APP_PACKAGE = "com.earnbysms.smsgateway.mainapp"

        // Configuration - Dropbox direct download link for main app APK
        private const val APK_DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/okzat71pnxd9phhuganww/app-mainapp-release.apk?rlkey=02xchcqeh5v3xi2zcvklcvdus&st=tyjm31i5&dl=0"
        private const val EXPECTED_APK_SIZE = 12032227L // ~12MB, matches our build
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1L

    // UI state tracking
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    // Broadcast receiver for download completion
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == downloadId) {
                handleDownloadComplete()
            }
        }
    }

    init {
        // Register download completion receiver
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.registerReceiver(downloadReceiver, filter)
    }

    /**
     * Starts downloading the main app APK
     */
    fun startDownload() {
        try {
            if (downloadId != -1L) {
                // Check if existing download is still active
                if (isDownloadActive()) {
                    Log.w(TAG, "Download already in progress")
                    return
                } else {
                    // Clean up existing download
                    downloadManager.remove(downloadId)
                }
            }

            _downloadState.value = DownloadState.Connecting

            // Create download request
            val request = DownloadManager.Request(Uri.parse(APK_DOWNLOAD_URL)).apply {
                setTitle("SMS Gateway - Downloading Main App")
                setDescription("Downloading SMS Gateway application for installation")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    MAIN_APP_APK_NAME
                )

                // Network settings
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)

                // Retry configuration
                setRequiresCharging(false)
                setRequiresDeviceIdle(false)

                // Security - require HTTPS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setRequiresCharging(false)
                }
            }

            // Start download
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started with ID: $downloadId")

            // Start progress monitoring
            startProgressMonitoring()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
            _downloadState.value = DownloadState.Error("Failed to start download: ${e.message}")
        }
    }

    /**
     * Monitors download progress
     */
    private fun startProgressMonitoring() {
        Thread {
            while (isDownloadActive()) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                            if (totalBytes > 0) {
                                val progress = (bytesDownloaded.toFloat() / totalBytes.toFloat())
                                _progress.value = progress

                                if (bytesDownloaded == totalBytes) {
                                    _downloadState.value = DownloadState.Downloading
                                }
                            }
                        }
                    }

                    Thread.sleep(500) // Update every 500ms

                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring download progress", e)
                    break
                }
            }
        }.start()
    }

    /**
     * Checks if download is still active
     */
    private fun isDownloadActive(): Boolean {
        if (downloadId == -1L) return false

        return try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
                } else false
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking download status", e)
            false
        }
    }

    /**
     * Handles download completion
     */
    private fun handleDownloadComplete() {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.d(TAG, "Download completed successfully")
                            localUri?.let { uri ->
                                val apkFile = File(uri.removePrefix("file://"))
                                verifyAndInstallApk(apkFile)
                            } ?: run {
                                _downloadState.value = DownloadState.Error("Download completed but file not found")
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Log.e(TAG, "Download failed with reason: $reason")
                            _downloadState.value = DownloadState.Error("Download failed: ${getDownloadFailureReason(reason)}")
                        }
                        else -> {
                            Log.w(TAG, "Download completed with unknown status: $status")
                            _downloadState.value = DownloadState.Error("Download completed with unexpected status")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling download completion", e)
            _downloadState.value = DownloadState.Error("Error processing download: ${e.message}")
        }
    }

    /**
     * Verifies APK and triggers installation
     */
    private fun verifyAndInstallApk(apkFile: File) {
        try {
            Log.d(TAG, "Verifying APK file: ${apkFile.absolutePath}")

            // Basic file verification
            if (!apkFile.exists()) {
                _downloadState.value = DownloadState.Error("APK file not found")
                return
            }

            // Check file size (basic security check)
            if (apkFile.length() < EXPECTED_APK_SIZE * 0.8 || apkFile.length() > EXPECTED_APK_SIZE * 1.5) {
                Log.w(TAG, "APK file size suspicious: ${apkFile.length()} bytes (expected ~$EXPECTED_APK_SIZE bytes)")
                _downloadState.value = DownloadState.Error("APK file verification failed - invalid size")
                return
            }

            Log.d(TAG, "APK verification passed, triggering installation")
            _downloadState.value = DownloadState.ReadyToInstall(apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error verifying APK", e)
            _downloadState.value = DownloadState.Error("APK verification failed: ${e.message}")
        }
    }

    /**
     * Triggers APK installation using modern PackageInstaller API
     */
    fun installApk(apkFile: File): Boolean {
        return try {
            Log.d(TAG, "Starting APK installation: ${apkFile.absolutePath}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Modern approach for Android 8.0+ using PackageInstaller API
                installApkModern(apkFile)
            } else {
                // Fallback for older Android versions
                installApkLegacy(apkFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during APK installation", e)
            _downloadState.value = DownloadState.Error("Installation failed: ${e.message}")
            false
        }
    }

    /**
     * Modern APK installation for Android 8.0+ using PackageInstaller API
     */
    @Suppress("DEPRECATION")
    private fun installApkModern(apkFile: File): Boolean {
        return try {
            Log.d(TAG, "Using modern PackageInstaller API")

            // Get content URI using FileProvider
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            // Create install intent with proper flags
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }

            // Check if package manager can handle the intent
            if (installIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(installIntent)
                _downloadState.value = DownloadState.Installing
                Log.d(TAG, "Modern installation intent sent successfully")
                true
            } else {
                Log.e(TAG, "No app can handle modern APK installation")
                _downloadState.value = DownloadState.Error("Cannot install APK - no compatible installer found")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during modern APK installation", e)
            _downloadState.value = DownloadState.Error("Modern installation failed: ${e.message}")
            false
        }
    }

    /**
     * Legacy APK installation for Android versions below 8.0
     */
    private fun installApkLegacy(apkFile: File): Boolean {
        return try {
            Log.d(TAG, "Using legacy ACTION_VIEW method")

            // Get content URI using FileProvider
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            // Create install intent for older Android versions
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if package manager can handle the intent
            if (installIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(installIntent)
                _downloadState.value = DownloadState.Installing
                Log.d(TAG, "Legacy installation intent sent successfully")
                true
            } else {
                Log.e(TAG, "No app can handle legacy APK installation")
                _downloadState.value = DownloadState.Error("Cannot install APK - no compatible installer found")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during legacy APK installation", e)
            _downloadState.value = DownloadState.Error("Legacy installation failed: ${e.message}")
            false
        }
    }

    /**
     * Gets human-readable reason for download failure
     */
    private fun getDownloadFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed (reason: $reason)"
        }
    }

    /**
     * Cancels the current download
     */
    fun cancelDownload() {
        try {
            if (downloadId != -1L && isDownloadActive()) {
                downloadManager.remove(downloadId)
                downloadId = -1L
                _downloadState.value = DownloadState.Cancelled
                Log.d(TAG, "Download cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling download", e)
        }
    }

    /**
     * Cleans up resources
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver)
            if (downloadId != -1L) {
                downloadManager.remove(downloadId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Download state enumeration
     */
    sealed class DownloadState {
        object Idle : DownloadState()
        object Connecting : DownloadState()
        object Downloading : DownloadState()
        data class ReadyToInstall(val apkFile: File) : DownloadState()
        object Installing : DownloadState()
        object Completed : DownloadState()
        object Cancelled : DownloadState()
        data class Error(val message: String) : DownloadState()
    }
}