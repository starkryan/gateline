package com.earnbysms.smsgateway.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.earnbysms.smsgateway.R
import com.earnbysms.smsgateway.data.repository.GatewayRepository
import kotlinx.coroutines.*

/**
 * SMS Gateway Service - Runs in foreground and forwards SMS in real-time
 * Heartbeat every 30 seconds for optimal device monitoring
 */
class SMSGatewayService : Service() {

    companion object {
        private const val TAG = "SMSGatewayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_gateway_channel"
        private const val CHANNEL_NAME = "SMS Gateway Service"
        private const val HEARTBEAT_INTERVAL_MS = 60000L // 60 seconds - balanced responsiveness
    }

    private lateinit var gatewayRepository: GatewayRepository

    private lateinit var serviceScope: CoroutineScope
    private var messageCount = 0
    private var serviceStartTime = 0L
    private var heartbeatJob: Job? = null
    private var consecutiveHeartbeatFailures = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceStartTime = System.currentTimeMillis()

        // Initialize repository manually (without Hilt)
        try {
            val gatewayApi = com.earnbysms.smsgateway.data.remote.api.ApiProvider.gatewayApi
            gatewayRepository = com.earnbysms.smsgateway.data.repository.GatewayRepository(gatewayApi, this, com.google.gson.Gson())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        when (intent?.action) {
            "STOP_SERVICE" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "SMS_RECEIVED" -> {
                // Handle SMS received from receiver
                val sender = intent.getStringExtra("sender") ?: "Unknown"
                val body = intent.getStringExtra("body") ?: ""
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
                val subscriptionId = intent.getIntExtra("subscriptionId", -1).takeIf { it != -1 }
                val simSlot = intent.getIntExtra("simSlot", -1).takeIf { it != -1 }
                val receivingNumber = intent.getStringExtra("receivingNumber") ?: "Unknown"

                Log.d(TAG, "Processing SMS intent: $sender -> $receivingNumber (${body.take(30)}...)")

                // Forward the SMS directly
                onSMSReceived(sender, body, subscriptionId, simSlot, receivingNumber)
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize device registration
        serviceScope.launch {
            try {
                val registrationSuccess = gatewayRepository.registerDevice()
                if (registrationSuccess.isSuccess) {
                    Log.d(TAG, "Device registered successfully")
                    // Start periodic heartbeat every 30 seconds
                    startPeriodicHeartbeat()
                } else {
                    Log.e(TAG, "Failed to register device: ${registrationSuccess.exceptionOrNull()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register device", e)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        heartbeatJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service stopped. Total messages processed: $messageCount")
    }

    /**
     * Called when a new SMS message is received
     */
    fun onSMSReceived(sender: String, body: String, subscriptionId: Int? = null, simSlot: Int? = null, receivingNumber: String = "Unknown") {
        messageCount++
        Log.d(TAG, "SMS received from $sender to $receivingNumber: ${body.take(50)}...")

        serviceScope.launch {
            try {
                val result = gatewayRepository.forwardSms(sender, body, subscriptionId, simSlot, receivingNumber)
                if (result.isSuccess) {
                    Log.d(TAG, "SMS forwarded successfully")
                } else {
                    Log.e(TAG, "Failed to forward SMS: ${result.exceptionOrNull()}")
                }
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward SMS", e)
            }
        }
    }

    /**
     * Update notification with current stats
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Create notification channel for Android 8+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS Gateway Service - Forwarding messages"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground service notification
     */
    private fun createNotification(): Notification {
        val uptime = System.currentTimeMillis() - serviceStartTime
        val uptimeMinutes = uptime / 60000
        val heartbeatStatus = if (heartbeatJob?.isActive == true) {
            "✓ Active (60s interval)"
        } else {
            "⚠ Inactive"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway Active")
            .setContentText("Messages: $messageCount | Uptime: ${uptimeMinutes}min | $heartbeatStatus")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Start periodic heartbeat every 30 seconds for optimal responsiveness
     */
    private fun startPeriodicHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                try {
                    val result = gatewayRepository.sendHeartbeat()
                    if (result.isSuccess) {
                        Log.d(TAG, "Heartbeat sent successfully (60s interval)")
                        consecutiveHeartbeatFailures = 0
                        updateNotification()
                    } else {
                        consecutiveHeartbeatFailures++
                        Log.w(TAG, "Heartbeat failed ($consecutiveHeartbeatFailures/3 attempts)")

                        // After 3 consecutive failures, increase interval
                        if (consecutiveHeartbeatFailures >= 3) {
                            Log.w(TAG, "Multiple heartbeat failures, increasing interval to 60s")
                            delay(60000) // Increase to 60 seconds after failures
                        }
                    }

                    // Standard 30-second heartbeat
                    delay(HEARTBEAT_INTERVAL_MS)

                    // Reset failure count after successful heartbeat cycle
                    if (consecutiveHeartbeatFailures == 0) {
                        updateNotification()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error sending heartbeat", e)
                    consecutiveHeartbeatFailures++
                    delay(HEARTBEAT_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Get service statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "messageCount" to messageCount,
            "uptime" to (System.currentTimeMillis() - serviceStartTime),
            "isRunning" to true,
            "heartbeatActive" to (heartbeatJob?.isActive == true),
            "heartbeatInterval" to "${HEARTBEAT_INTERVAL_MS}ms",
            "consecutiveFailures" to consecutiveHeartbeatFailures,
            "lastHeartbeat" to (if (heartbeatJob?.isActive == true) System.currentTimeMillis() else 0)
        )
    }
}