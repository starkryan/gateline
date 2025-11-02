package com.earnbysms.smsgateway.presentation.activity

import android.Manifest
import android.content.Intent
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
import kotlinx.coroutines.delay

/**
 * Installer MainActivity - Focuses on device identification and setup
 * This variant handles device registration and preparation for main app
 */
class InstallerMainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InstallerMainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "Installer MainActivity onCreate")
        Log.d(TAG, "Build Variant - Installer: ${BuildConfig.IS_INSTALLER_VARIANT}")
        Log.d(TAG, "Build Variant - MainApp: ${BuildConfig.IS_MAINAPP_VARIANT}")

        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InstallerMainScreen()
                }
            }
        }

        // Check and request permissions
        checkAndRequestPermissions()
    }

    private fun hasAllRequiredPermissions(): Boolean {
        val permissions = getInstallerPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getInstallerPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun checkAndRequestPermissions() {
        if (hasAllRequiredPermissions()) {
            showDeviceInfoScreen()
        } else {
            permissionLauncher.launch(getInstallerPermissions())
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val requiredPermissions = getInstallerPermissions()
        val allGranted = requiredPermissions.all {
            permissions[it] == true
        }

        if (allGranted) {
            Log.d(TAG, "Installer permissions granted, showing device info")
            showDeviceInfoScreen()
        } else {
            val deniedPermissions = requiredPermissions.filter {
                permissions[it] != true
            }
            Log.w(TAG, "Installer permissions denied: $deniedPermissions")
            showPermissionErrorScreen(deniedPermissions.toList())
        }
    }

    private fun showDeviceInfoScreen() {
        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceInfoScreen()
                }
            }
        }
    }

    private fun showPermissionErrorScreen(deniedPermissions: List<String>) {
        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InstallerPermissionErrorScreen(deniedPermissions)
                }
            }
        }
    }

    @Composable
    fun InstallerMainScreen() {
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(1000)
            isLoading = false
        }

        if (isLoading) {
            InstallerLoadingScreen()
        } else {
            DeviceInfoScreen()
        }
    }

    @Composable
    fun InstallerLoadingScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Preparing Device Setup...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    @Composable
    fun DeviceInfoScreen() {
        var deviceInfo by remember { mutableStateOf(mapOf<String, String>()) }
        var isPersistent by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Collect device information
            val info = mutableMapOf<String, String>()

            // Basic device info
            info["Device ID"] = DeviceUtils.getDeviceId(this@InstallerMainActivity)
            info["Phone Number"] = DeviceUtils.getPhoneNumber(this@InstallerMainActivity)
            info["Manufacturer"] = android.os.Build.MANUFACTURER
            info["Model"] = android.os.Build.MODEL
            info["Android Version"] = android.os.Build.VERSION.RELEASE
            info["SDK Version"] = android.os.Build.VERSION.SDK_INT.toString()

            // Persistent ID info
            val persistentInfo = PersistentDeviceId.getDeviceInfo(this@InstallerMainActivity)
            info.putAll(persistentInfo)
            isPersistent = PersistentDeviceId.isPersistentId(this@InstallerMainActivity)

            deviceInfo = info

            // Log comprehensive device info
            PersistentDeviceId.logDeviceInfo(this@InstallerMainActivity)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isPersistent) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = "Device Status",
                modifier = Modifier.size(64.dp),
                tint = if (isPersistent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Device Setup Complete",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Device has been registered and is ready for SMS Gateway operations.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            if (isPersistent) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ Persistent device ID detected - survives app reinstalls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Device information cards
            deviceInfo.forEach { (key, value) ->
                DeviceInfoCard(
                    title = key,
                    value = value
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // Create intent to launch main app
                    val mainAppIntent = packageManager.getLaunchIntentForPackage("com.earnbysms.smsgateway.mainapp")
                    if (mainAppIntent != null) {
                        startActivity(mainAppIntent)
                        finish()
                    } else {
                        Log.w(TAG, "Main app not installed, showing install prompt")
                        // Handle case where main app is not installed
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Launch SMS Gateway")
            }
        }
    }

    @Composable
    fun DeviceInfoCard(
        title: String,
        value: String
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    fun InstallerPermissionErrorScreen(deniedPermissions: List<String>) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Installer Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The installer requires phone and network permissions to register your device.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            deniedPermissions.forEach { permission ->
                Text(
                    text = "• $permission",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { checkAndRequestPermissions() }
            ) {
                Text("Retry Permissions")
            }
        }
    }
}