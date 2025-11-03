package com.earnbysms.smsgateway.presentation.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.earnbysms.smsgateway.presentation.service.SMSGatewayService
import com.earnbysms.smsgateway.utils.PersistentDeviceId
import com.earnbysms.smsgateway.utils.SimSlotInfoCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SMS Receiver - Intercepts SMS messages and forwards them to the service
 * Enhanced with comprehensive multi-SIM support and phone number detection
 */
class SMSReceiver : BroadcastReceiver() {

    
    companion object {
        private const val TAG = "SMSReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        Log.d(TAG, "🚀 NEW SMS RECEIVED - Using Fixed Logic!")

        try {
            // Extract SMS messages from intent
            val messages = extractMessages(intent)

            if (messages.isEmpty()) {
                Log.w(TAG, "No SMS messages found in intent")
                return
            }

            // Get subscription info for multi-SIM support using slot-based detection (most reliable)
            val slotIndex = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)
            val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it != -1 }

            Log.d(TAG, "🎯 SMS Intent Analysis - Slot: $slotIndex, SubId: $subscriptionId")

            // Handle multipart SMS by concatenating all message parts
            if (messages.isNotEmpty()) {
                val sender = messages[0].originatingAddress ?: "Unknown"
                val timestamp = messages[0].timestampMillis

                // Concatenate all message parts to handle long SMS properly
                val fullMessageBody = messages.joinToString("") { it.messageBody ?: "" }

                Log.d(TAG, "Concatenated ${messages.size} SMS parts into single message (${fullMessageBody.length} chars)")

                // Get the receiving phone number using intent extras-based detection (Rewardly approach)
                val receivingNumber = getReceivingPhoneNumber(context, intent)
                Log.d(TAG, "📱 Receiving number detected: $receivingNumber")

                // Enhanced: Log comprehensive SIM information for debugging
                val simInfo = SimSlotInfoCollector.getFormattedSimInfo(context)
                Log.d(TAG, "📊 Active SIMs: $simInfo")

                // Enhanced: Log device information for tracking
                PersistentDeviceId.logDeviceInfo(context)

                // Forward the complete message to the service
                forwardCompleteMessage(context, sender, fullMessageBody, timestamp, subscriptionId, slotIndex, receivingNumber)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    /**
     * Extract SMS messages from the intent
     */
    private fun extractMessages(intent: Intent): Array<SmsMessage> {
        return when (intent.action) {
            "android.provider.Telephony.SMS_RECEIVED" -> {
                val pdus = intent.getSerializableExtra("pdus") as? Array<ByteArray>
                val format = intent.getStringExtra("format")

                pdus?.mapNotNull { pdu ->
                    pdu?.let { SmsMessage.createFromPdu(it, format) }
                }?.toTypedArray() ?: emptyArray()
            }

            "android.provider.Telephony.SMS_DELIVER" -> {
                // For target SDK 23+, messages are delivered differently
                val pdus = intent.getSerializableExtra("pdus") as? Array<ByteArray>
                val format = intent.getStringExtra("format")

                pdus?.mapNotNull { pdu ->
                    pdu?.let { SmsMessage.createFromPdu(it, format) }
                }?.toTypedArray() ?: emptyArray()
            }

            else -> {
                Log.w(TAG, "Unknown SMS intent action: ${intent.action}")
                emptyArray()
            }
        }
    }

    
    /**
     * Forward complete SMS message (concatenated multipart) to the gateway service
     */
    private fun forwardCompleteMessage(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int?,
        simSlot: Int?,
        receivingNumber: String
    ) {
        try {
            Log.d(TAG, "Forwarding complete SMS: $sender -> ${body.take(50)}... (${body.length} chars total)")

            // Start or communicate with the service
            val serviceIntent = Intent(context, SMSGatewayService::class.java).apply {
                action = "SMS_RECEIVED"
                putExtra("sender", sender)
                putExtra("body", body)
                putExtra("timestamp", timestamp)
                putExtra("subscriptionId", subscriptionId)
                putExtra("simSlot", simSlot)
                putExtra("receivingNumber", receivingNumber)
            }

            // Forward the message to the running service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Also directly notify the service if it's running
            try {
                val intent = Intent("SMS_GATEWAY_MESSAGE")
                intent.putExtra("sender", sender)
                intent.putExtra("body", body)
                intent.putExtra("subscriptionId", subscriptionId)
                intent.putExtra("simSlot", simSlot)
                intent.putExtra("receivingNumber", receivingNumber)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not directly notify service", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding SMS to service", e)
        }
    }

  
    /**
     * Get the receiving phone number using the same successful method as GatewayRepository
     * This uses SubscriptionManager.getPhoneNumber which has higher privileges than SMS intent extras
     */
    private fun getReceivingPhoneNumber(context: Context, intent: Intent?): String {
        return try {
            if (intent == null) {
                Log.w(TAG, "Intent is null, cannot determine receiving number")
                return "Unknown"
            }

            // Extract subscription info from SMS intent extras (most accurate)
            val subscriptionId = intent.getIntExtra("subscription", -1)
            val slotIndex = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)

            Log.d(TAG, "SMS Intent extras - subscriptionId: $subscriptionId, slotIndex: $slotIndex")

            // Use the same successful method as GatewayRepository
            if (subscriptionId != -1) {
                val phoneNumber = getPhoneNumberFromSubscriptionManager(context, subscriptionId)
                if (phoneNumber != "Unknown") {
                    Log.d(TAG, "✅ Found receiving number via SubscriptionManager.getPhoneNumber: $phoneNumber")
                    // Get carrier name for better identification
                    val carrierName = getCarrierNameForSubscription(context, subscriptionId)
                    return formatPhoneNumberWithCarrier(phoneNumber, carrierName, slotIndex)
                }
            }

            Log.w(TAG, "❌ Could not determine receiving phone number")
            "Unknown"
        } catch (e: SecurityException) {
            Log.e(TAG, "🔒 Permission denied getting phone number", e)
            "Permission denied"
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error getting receiving phone number", e)
            "Unknown"
        }
    }

    /**
     * Get phone number using SubscriptionManager.getPhoneNumber (same method that works in GatewayRepository)
     */
    @Suppress("DEPRECATION")
    private fun getPhoneNumberFromSubscriptionManager(context: Context, subscriptionId: Int): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val number = subscriptionManager.getPhoneNumber(subscriptionId)
                if (!number.isNullOrEmpty() && number != "Unknown") {
                    return number
                }
            }
            "Unknown"
        } catch (e: Exception) {
            Log.d(TAG, "SubscriptionManager.getPhoneNumber failed", e)
            "Unknown"
        }
    }

    /**
     * Get carrier name for subscription
     */
    private fun getCarrierNameForSubscription(context: Context, subscriptionId: Int): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                subscriptionInfo?.carrierName?.toString() ?: "Unknown"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

  
    /**
     * Format phone number with carrier information for better clarity
     * Preserves original phone number format without modification
     */
    private fun formatPhoneNumberWithCarrier(phoneNumber: String, carrierName: String, slotIndex: Int): String {
        return "$phoneNumber (SIM$slotIndex: $carrierName)"
    }
}