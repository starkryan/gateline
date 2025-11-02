package com.earnbysms.smsgateway.presentation.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.earnbysms.smsgateway.presentation.ui.theme.SMSGatewayTheme
import java.io.File
import android.os.Environment
import android.webkit.MimeTypeMap
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.FileProvider

class InstallerMainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InstallerMainActivity"
        private const val DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/okzat71pnxd9phhuganww/app-mainapp-release.apk?rlkey=02xchcqeh5v3xi2zcvklcvdus&st=tyjm31i5&dl=1"
        private const val APK_FILE_NAME = "sms-gateway-latest.apk"
    }

    private var isDownloading = mutableStateOf(false)
    private var downloadProgress = mutableStateOf(0f)
    private var downloadedFile: File? = null
    private var isInstalling = mutableStateOf(false)
    private var installationCompleted = mutableStateOf(false)

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == downloadId) {
                handleDownloadComplete()
            }
        }
    }

    private var downloadId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        // Register download receiver
        registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )

        setContent {
            SMSGatewayTheme {
                InstallerScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    private fun checkAndRequestPermissions() {
        // No phone permissions required for installer variant
        Log.d(TAG, "Installer variant - no sensitive permissions required")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun InstallerScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Simple Update Button
            UpdateButton()
        }
    }

    @Composable
    private fun UpdateButton() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = if (isDownloading.value) Icons.Default.Settings else Icons.Default.Info,
                    contentDescription = "Download",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "SMS Gateway",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                when {
                    isDownloading.value -> {
                        Text(
                            text = "Downloading... ${downloadProgress.value.toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        LinearProgressIndicator(
                            progress = downloadProgress.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }

                    isInstalling.value -> {
                        Text(
                            text = "Installing...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }

                    installationCompleted.value -> {
                        Text(
                            text = "Installation Complete!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Main app has been installed successfully",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {
                        Text(
                            text = "Update Available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {
                                startDownload()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Now")
                        }
                    }
                }
            }
        }
    }

    private fun startDownload() {
        try {
            Log.d(TAG, "Starting download of main app APK")

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
                .setTitle("SMS Gateway Update")
                .setDescription("Downloading latest version of SMS Gateway")
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILE_NAME
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            downloadId = downloadManager.enqueue(request)
            isDownloading.value = true
            downloadProgress.value = 0f

            // Monitor download progress
            lifecycleScope.launch {
                monitorDownloadProgress()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            isDownloading.value = false
            android.widget.Toast.makeText(
                this,
                "Download failed: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private suspend fun monitorDownloadProgress() {
        // Simple simulation for demo purposes
        // In a real implementation, you would use DownloadManager.Query
        var progress = 0f
        while (isDownloading.value && progress < 95f) {
            delay(500)
            progress += 5f
            downloadProgress.value = progress
        }
    }

    private fun handleDownloadComplete() {
        try {
            Log.d(TAG, "Download completed")

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val localUri = it.getString(it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                        val reason = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_REASON))

                        Log.d(TAG, "Download status: $status, reason: $reason")
                        Log.d(TAG, "Downloaded file URI: $localUri")

                        // Get the actual file path from the URI
                        val fileUri = Uri.parse(localUri)
                        var filePath = fileUri.path

                        // If the URI is a content URI, resolve it to a file path
                        if (filePath != null && filePath.startsWith("file://")) {
                            filePath = filePath.substring(7) // Remove "file://" prefix
                        }

                        if (filePath != null) {
                            downloadedFile = File(filePath)
                            Log.d(TAG, "Downloaded file path: ${downloadedFile?.absolutePath}")
                            Log.d(TAG, "Downloaded file exists: ${downloadedFile?.exists()}")
                            Log.d(TAG, "Downloaded file size: ${downloadedFile?.length()} bytes")

                            if (downloadedFile?.exists() == true && downloadedFile?.length()!! > 1000000) { // At least 1MB
                                isDownloading.value = false
                                downloadProgress.value = 100f

                                android.widget.Toast.makeText(
                                    this,
                                    "Download completed! Starting installation...",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()

                                // Automatically start installation
                                installApp()
                            } else {
                                Log.e(TAG, "Downloaded file is invalid or too small")
                                isDownloading.value = false
                                android.widget.Toast.makeText(
                                    this,
                                    "Downloaded file is invalid",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Log.e(TAG, "Failed to parse download file path from URI: $localUri")
                            isDownloading.value = false
                            android.widget.Toast.makeText(
                                this,
                                "Failed to locate downloaded file",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val reason = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_REASON))
                        Log.e(TAG, "Download failed with status: $status, reason: $reason")
                        isDownloading.value = false
                        android.widget.Toast.makeText(
                            this,
                            "Download failed: $reason",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling download complete", e)
            isDownloading.value = false
            android.widget.Toast.makeText(
                this,
                "Error completing download",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun installApp() {
        try {
            Log.d(TAG, "Starting app installation")
            isInstalling.value = true

            val file = downloadedFile
            if (file != null && file.exists()) {
                Log.d(TAG, "File path: ${file.absolutePath}")
                Log.d(TAG, "File size: ${file.length()} bytes")
                Log.d(TAG, "File readable: ${file.canRead()}")

                // Validate file size before attempting installation
                if (file.length() < 1000000) { // Less than 1MB is likely invalid
                    Log.e(TAG, "Downloaded file is too small: ${file.length()} bytes")
                    android.widget.Toast.makeText(
                        this,
                        "Downloaded file appears to be invalid (${file.length()} bytes)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                }

                val contentUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

                Log.d(TAG, "Content URI: $contentUri")

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Verify that the package installer can handle this intent
                val packageInstaller = packageManager.resolveActivity(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (packageInstaller != null) {
                    Log.d(TAG, "Starting installation intent")
                    startActivity(installIntent)
                } else {
                    Log.e(TAG, "No package installer found to handle the intent")
                    isInstalling.value = false
                    android.widget.Toast.makeText(
                        this,
                        "Package installer not available",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Log.e(TAG, "Downloaded file not found or null")
                isInstalling.value = false
                android.widget.Toast.makeText(
                    this,
                    "Please download the app first",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "FileProvider URI error", e)
            isInstalling.value = false
            android.widget.Toast.makeText(
                this,
                "File access error: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error installing app", e)
            isInstalling.value = false
            android.widget.Toast.makeText(
                this,
                "Installation failed: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}