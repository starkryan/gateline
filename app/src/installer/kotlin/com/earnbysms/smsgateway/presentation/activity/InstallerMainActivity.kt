package com.earnbysms.smsgateway.presentation.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.earnbysms.smsgateway.BuildConfig
import com.earnbysms.smsgateway.presentation.ui.theme.SMSGatewayTheme
import com.earnbysms.smsgateway.utils.PersistentDeviceId
import com.earnbysms.smsgateway.utils.DeviceUtils
import com.earnbysms.smsgateway.service.AppUpdateDownloader
import kotlinx.coroutines.delay

class InstallerMainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InstallerMainActivity"
    }

    private var appUpdateDownloader: AppUpdateDownloader? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Phone state permission granted")
        } else {
            Log.w(TAG, "Phone state permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        // Initialize the download manager
        appUpdateDownloader = AppUpdateDownloader(this)

        setContent {
            SMSGatewayTheme {
                InstallerScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateDownloader?.cleanup()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Phone state permission already granted")
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun InstallerScreen() {
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(1000) // Simulate initial loading
            isLoading = false
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Header
            AppHeader()

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = "Initializing installer...",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // App Information
                AppInfoCard()

                // Device Information
                DeviceInfoCard()

                // Action Buttons
                ActionButtons()
            }
        }
    }

    @Composable
    private fun AppHeader() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "SMS Gateway",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "SMS Gateway Installer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun AppInfoCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Application Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                InfoItem(
                    icon = Icons.Default.Info,
                    label = "Package Name",
                    value = BuildConfig.APPLICATION_ID
                )

                InfoItem(
                    icon = Icons.Default.Settings,
                    label = "Build Type",
                    value = if (BuildConfig.IS_INSTALLER_VARIANT) "Installer" else "Unknown"
                )

                InfoItem(
                    icon = Icons.Default.Build,
                    label = "Version Code",
                    value = BuildConfig.VERSION_CODE.toString()
                )
            }
        }
    }

    @Composable
    private fun DeviceInfoCard() {
        val deviceId = remember { PersistentDeviceId.getDeviceId(this@InstallerMainActivity) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Device Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                InfoItem(
                    icon = Icons.Default.Settings,
                    label = "Device ID",
                    value = deviceId
                )

                InfoItem(
                    icon = Icons.Default.Info,
                    label = "Android Version",
                    value = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                )

                InfoItem(
                    icon = Icons.Default.Settings,
                    label = "Security",
                    value = "Signed Build"
                )
            }
        }
    }

    @Composable
    private fun ActionButtons() {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                Text("Install Main App")
            }

            OutlinedButton(
                onClick = {
                    finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Close Installer")
            }
        }
    }

    private fun startDownload() {
        try {
            Log.d(TAG, "Starting download of main app APK")
            appUpdateDownloader?.startDownload()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
        }
    }

    @Composable
    private fun InfoItem(
        icon: ImageVector,
        label: String,
        value: String
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}