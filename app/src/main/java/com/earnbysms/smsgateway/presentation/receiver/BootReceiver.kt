package com.earnbysms.smsgateway.presentation.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.earnbysms.smsgateway.presentation.service.SMSGatewayService

/**
 * Boot Receiver - Auto-starts the SMS Gateway service on device boot
 * Ensures continuous operation across device restarts
 */
class BootReceiver : BroadcastReceiver() {

  
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        Log.d(TAG, "Boot receiver triggered: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                startSMSGatewayService(context)
            }
        }
    }

    /**
     * Start the SMS Gateway service
     */
    private fun startSMSGatewayService(context: Context) {
        try {
            val serviceIntent = Intent(context, SMSGatewayService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d(TAG, "SMS Gateway service auto-started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-start SMS Gateway service", e)
        }
    }
}