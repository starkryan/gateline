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
import com.earnbysms.smsgateway.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity - Entry point for SMS Gateway app
 * Handles permissions, service startup, and user interface
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Permission launcher for multiple permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "MainActivity onCreate")

        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        // Check and request permissions
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume")
    }

    /**
     * Check if all critical permissions are granted
     */
    private fun hasAllCriticalPermissions(): Boolean {
        val permissions = getCriticalPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get list of critical permissions that must be granted for basic functionality
     */
    private fun getCriticalPermissions(): Array<String> {
        return arrayOf(
            // Critical SMS permissions
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,

            // Critical Phone permissions
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    }

    /**
     * Get list of all permissions to request (including optional ones)
     */
    private fun getAllPermissions(): Array<String> {
        val permissions = mutableListOf<String>().apply {
            // Add critical permissions first
            addAll(getCriticalPermissions())

            // Add non-critical permissions
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.INTERNET)
            add(Manifest.permission.ACCESS_NETWORK_STATE)
            add(Manifest.permission.RECEIVE_BOOT_COMPLETED)

            // Storage permissions (optional for this app)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            // Notification permission for Android 13+ (optional)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Foreground service permissions for Android 15
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
                add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
            } else {
                add(Manifest.permission.FOREGROUND_SERVICE)
            }
        }

        return permissions.toTypedArray()
    }

    /**
     * Check and request permissions if needed
     */
    private fun checkAndRequestPermissions() {
        if (hasAllCriticalPermissions()) {
            // Critical permissions granted, start the service
            startSMSGatewayService()
        } else {
            // Request all permissions (but only require critical ones)
            permissionLauncher.launch(getAllPermissions())
        }
    }

    /**
     * Handle permission request results
     */
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val criticalPermissions = getCriticalPermissions()
        val criticalPermissionsGranted = criticalPermissions.all {
            permissions[it] == true
        }

        if (criticalPermissionsGranted) {
            Log.d(TAG, "Critical permissions granted, starting service")
            startSMSGatewayService()
        } else {
            val deniedCriticalPermissions = criticalPermissions.filter {
                permissions[it] != true
            }
            Log.w(TAG, "Critical permissions denied: $deniedCriticalPermissions")

            // Show error message to user only for critical permissions
            setContent {
                SMSGatewayTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PermissionErrorScreen(deniedCriticalPermissions.toList())
                    }
                }
            }
        }

        // Log any non-critical permissions that were denied (for info only)
        val deniedNonCriticalPermissions = permissions.filter {
            !criticalPermissions.contains(it.key) && !it.value
        }.keys
        if (deniedNonCriticalPermissions.isNotEmpty()) {
            Log.i(TAG, "Non-critical permissions denied (app will still function): $deniedNonCriticalPermissions")
        }
    }

    /**
     * Show permissions granted screen
     */
    private fun showPermissionsGrantedScreen() {
        setContent {
            SMSGatewayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServiceRunningScreen()
                }
            }
        }
    }

    /**
     * Start the SMS Gateway Service
     */
    private fun startSMSGatewayService() {
        try {
            val serviceIntent = Intent(this, SMSGatewayService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.d(TAG, "SMS Gateway Service started successfully")

            // Update UI to show service is running
            setContent {
                SMSGatewayTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ServiceRunningScreen()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SMS Gateway Service", e)
            // Show error to user
            setContent {
                SMSGatewayTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ServiceErrorScreen(e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            // Simulate initial loading
            delay(1000)
            isLoading = false
        }

        if (isLoading) {
            LoadingScreen()
        } else {
            ServiceRunningScreen()
        }
    }

    @Composable
    fun LoadingScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Starting SMS Gateway...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    @Composable
    fun ServiceRunningScreen() {
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
                text = "The SMS Gateway service is running in the background, forwarding messages in real-time.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature cards
            FeatureCard(
                icon = Icons.Default.Settings,
                title = "Real-time Forwarding",
                description = "Forwards SMS messages immediately without storage"
            )

            FeatureCard(
                icon = Icons.Default.CheckCircle,
                title = "No Local Storage",
                description = "Messages are forwarded directly, nothing stored on device"
            )

            FeatureCard(
                icon = Icons.Default.Info,
                title = "Privacy-focused",
                description = "Minimal device data, secure forwarding"
            )
        }
    }

    @Composable
    fun ServiceErrorScreen(error: String) {
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
    fun PermissionErrorScreen(deniedPermissions: List<String>) {
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
                text = "Permissions Required",
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

    @Composable
    fun FeatureCard(
        icon: ImageVector,
        title: String,
        description: String
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
}