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
import com.earnbysms.smsgateway.presentation.service.SMSGatewayService
import com.earnbysms.smsgateway.presentation.ui.theme.SMSGatewayTheme
import com.earnbysms.smsgateway.utils.PersistentDeviceId
import com.earnbysms.smsgateway.utils.DeviceUtils
import com.earnbysms.smsgateway.utils.SimSlotInfoCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main App MainActivity - Full SMS Gateway functionality
 * This variant provides complete SMS monitoring and forwarding capabilities
 */
class MainAppMainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainAppMainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "Main App MainActivity onCreate")
        Log.d(TAG, "Build Variant - Installer: ${BuildConfig.IS_INSTALLER_VARIANT}")
        Log.d(TAG, "Build Variant - MainApp: ${BuildConfig.IS_MAINAPP_VARIANT}")

        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppMainScreen()
                }
            }
        }

        // Check and request permissions
        checkAndRequestPermissions()
    }

    private fun hasAllCriticalPermissions(): Boolean {
        val permissions = getMainAppCriticalPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getMainAppCriticalPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    }

    private fun getAllMainAppPermissions(): Array<String> {
        val permissions = mutableListOf<String>().apply {
            addAll(getMainAppCriticalPermissions())
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.INTERNET)
            add(Manifest.permission.ACCESS_NETWORK_STATE)
            add(Manifest.permission.RECEIVE_BOOT_COMPLETED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
                add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
            } else {
                add(Manifest.permission.FOREGROUND_SERVICE)
            }
        }

        return permissions.toTypedArray()
    }

    private fun checkAndRequestPermissions() {
        if (hasAllCriticalPermissions()) {
            startSMSGatewayService()
        } else {
            permissionLauncher.launch(getAllMainAppPermissions())
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val criticalPermissions = getMainAppCriticalPermissions()
        val criticalPermissionsGranted = criticalPermissions.all {
            permissions[it] == true
        }

        if (criticalPermissionsGranted) {
            Log.d(TAG, "Main app critical permissions granted, starting service")
            startSMSGatewayService()
        } else {
            val deniedCriticalPermissions = criticalPermissions.filter {
                permissions[it] != true
            }
            Log.w(TAG, "Main app critical permissions denied: $deniedCriticalPermissions")
            showMainAppPermissionErrorScreen(deniedCriticalPermissions.toList())
        }

        val deniedNonCriticalPermissions = permissions.filter {
            !criticalPermissions.contains(it.key) && !it.value
        }.keys
        if (deniedNonCriticalPermissions.isNotEmpty()) {
            Log.i(TAG, "Non-critical permissions denied: $deniedNonCriticalPermissions")
        }
    }

    private fun startSMSGatewayService() {
        try {
            val serviceIntent = Intent(this, SMSGatewayService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.d(TAG, "SMS Gateway Service started successfully")
            showMainAppServiceRunningScreen()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SMS Gateway Service", e)
            showMainAppServiceErrorScreen(e.message ?: "Unknown error")
        }
    }

    private fun showMainAppServiceRunningScreen() {
        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppServiceRunningScreen()
                }
            }
        }
    }

    private fun showMainAppServiceErrorScreen(error: String) {
        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppServiceErrorScreen(error)
                }
            }
        }
    }

    private fun showMainAppPermissionErrorScreen(deniedPermissions: List<String>) {
        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppPermissionErrorScreen(deniedPermissions)
                }
            }
        }
    }

    @Composable
    fun MainAppMainScreen() {
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(1000)
            isLoading = false
        }

        if (isLoading) {
            MainAppLoadingScreen()
        } else {
            MainAppServiceRunningScreen()
        }
    }

    @Composable
    fun MainAppLoadingScreen() {
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
                text = "Starting SMS Gateway...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    @Composable
    fun MainAppServiceRunningScreen() {
        var simInfo by remember { mutableStateOf("Loading SIM information...") }
        var deviceInfo by remember { mutableStateOf("Loading device information...") }
        var serviceStats by remember { mutableStateOf(mapOf<String, String>()) }

        LaunchedEffect(Unit) {
            // Collect SIM information
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    simInfo = SimSlotInfoCollector.getFormattedSimInfo(this@MainAppMainActivity)
                } catch (e: Exception) {
                    simInfo = "SIM info unavailable: ${e.message}"
                }
            }

            // Collect device information
            val info = mutableMapOf<String, String>()
            info["Device ID"] = DeviceUtils.getDeviceId(this@MainAppMainActivity)
            info["Phone Number"] = DeviceUtils.getPhoneNumber(this@MainAppMainActivity)
            info["Is Persistent ID"] = PersistentDeviceId.isPersistentId(this@MainAppMainActivity).toString()
            deviceInfo = info.map { "${it.key}: ${it.value}" }.joinToString("\n")

            // Service statistics
            serviceStats = mapOf(
                "Service Status" to "Running",
                "SMS Forwarding" to "Active",
                "Network Type" to "Connected",
                "Uptime" to "00:00:00"
            )
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
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Service Running",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SMS Gateway Active",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Full SMS Gateway functionality is running with real-time message forwarding.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Service Stats Card
            MainAppInfoCard(
                icon = Icons.Default.Speed,
                title = "Service Statistics",
                content = serviceStats.map { "${it.key}: ${it.value}" }.joinToString("\n")
            )

            // SIM Info Card
            MainAppInfoCard(
                icon = Icons.Default.SimCard,
                title = "SIM Information",
                content = simInfo
            )

            // Device Info Card
            MainAppInfoCard(
                icon = Icons.Default.Settings,
                title = "Device Information",
                content = deviceInfo
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature cards
            MainAppFeatureCard(
                icon = Icons.Default.Message,
                title = "Real-time SMS Forwarding",
                description = "Instant SMS message processing and forwarding"
            )

            MainAppFeatureCard(
                icon = Icons.Default.Security,
                title = "Secure Transmission",
                description = "Encrypted communication with remote server"
            )

            MainAppFeatureCard(
                icon = Icons.Default.Devices,
                title = "Multi-SIM Support",
                description = "Support for multiple SIM cards and carriers"
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Stop service and go back to installer
                        try {
                            val serviceIntent = Intent(this@MainAppMainActivity, SMSGatewayService::class.java)
                            stopService(serviceIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error stopping service", e)
                        }

                        val installerIntent = packageManager.getLaunchIntentForPackage("com.earnbysms.smsgateway.installer")
                        if (installerIntent != null) {
                            startActivity(installerIntent)
                            finish()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop Service")
                }

                Button(
                    onClick = {
                        // Restart service
                        startSMSGatewayService()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restart")
                }
            }
        }
    }

    @Composable
    fun MainAppInfoCard(
        icon: ImageVector,
        title: String,
        content: String
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun MainAppFeatureCard(
        icon: ImageVector,
        title: String,
        description: String
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    fun MainAppServiceErrorScreen(error: String) {
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
                contentDescription = "Service Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Service Error",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Failed to start SMS Gateway service",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { startSMSGatewayService() }
            ) {
                Text("Retry")
            }
        }
    }

    @Composable
    fun MainAppPermissionErrorScreen(deniedPermissions: List<String>) {
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
                text = "SMS Gateway Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This app requires SMS, Phone, and Storage permissions to function properly.",
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