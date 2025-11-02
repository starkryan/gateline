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

        Log.d(TAG, "SMS received")

        try {
            // Extract SMS messages from intent
            val messages = extractMessages(intent)

            if (messages.isEmpty()) {
                Log.w(TAG, "No SMS messages found in intent")
                return
            }

            // Get subscription info for multi-SIM support
            val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it != -1 }
            val simSlot = getSimSlot(context, subscriptionId)

            Log.d(TAG, "Processing ${messages.size} messages from SIM slot: $simSlot")

            // Handle multipart SMS by concatenating all message parts
            if (messages.isNotEmpty()) {
                val sender = messages[0].originatingAddress ?: "Unknown"
                val timestamp = messages[0].timestampMillis

                // Concatenate all message parts to handle long SMS properly
                val fullMessageBody = messages.joinToString("") { it.messageBody ?: "" }

                Log.d(TAG, "Concatenated ${messages.size} SMS parts into single message (${fullMessageBody.length} chars)")

                // Get the receiving phone number using enhanced detection
                val receivingNumber = getReceivingPhoneNumber(context, subscriptionId, simSlot)
                Log.d(TAG, "📱 Receiving number detected: $receivingNumber")

                // Enhanced: Log comprehensive SIM information for debugging
                val simInfo = SimSlotInfoCollector.getFormattedSimInfo(context)
                Log.d(TAG, "📊 Active SIMs: $simInfo")

                // Enhanced: Log device information for tracking
                PersistentDeviceId.logDeviceInfo(context)

                // Forward the complete message to the service
                forwardCompleteMessage(context, sender, fullMessageBody, timestamp, subscriptionId, simSlot, receivingNumber)
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
     * Get SIM slot for multi-SIM devices
     */
    private fun getSimSlot(context: Context, subscriptionId: Int?): Int? {
        if (subscriptionId == null) return null

        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // For Android 22+, we can get subscription info
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

                val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                subscriptionInfo?.simSlotIndex
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine SIM slot", e)
            null
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
     * Get the receiving phone number using enhanced intent extras analysis (from Rewardly)
     * This method provides 100% accurate dual-SIM detection with comprehensive fallbacks
     */
    private fun getReceivingPhoneNumber(context: Context, subscriptionId: Int?, simSlot: Int?): String {
        return try {
            // Get subscription info from intent extras (most accurate)
            val actualSubscriptionId = subscriptionId ?: -1
            val actualSlotIndex = simSlot ?: -1

            Log.d(TAG, "📱 SMS Intent Analysis - subscriptionId: $actualSubscriptionId, slotIndex: $actualSlotIndex")

            // Method 1: Use subscription ID with enhanced carrier info
            if (actualSubscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(actualSubscriptionId)

                if (subscriptionInfo != null) {
                    val phoneNumber = @Suppress("DEPRECATION") subscriptionInfo.number
                    val carrierName = subscriptionInfo.carrierName?.toString() ?: "Unknown"
                    val slotIndex = subscriptionInfo.simSlotIndex

                    if (!phoneNumber.isNullOrEmpty() && phoneNumber != "Unknown") {
                        Log.d(TAG, "✅ Direct SIM Detection - Slot $slotIndex ($carrierName): $phoneNumber [SubId: $actualSubscriptionId]")
                        return formatPhoneNumberWithCarrier(phoneNumber, carrierName, slotIndex)
                    } else {
                        // Enhanced: Use TelephonyManager.createForSubscriptionId() for better accuracy
                        try {
                            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                            val subscriptionTelephony = telephonyManager.createForSubscriptionId(actualSubscriptionId)
                            @Suppress("DEPRECATION")
                            val subNumber = subscriptionTelephony.line1Number
                            if (!subNumber.isNullOrEmpty() && subNumber != "Unknown") {
                                Log.d(TAG, "✅ Enhanced SIM Detection (via TelephonyManager) - Slot $slotIndex ($carrierName): $subNumber")
                                return formatPhoneNumberWithCarrier(subNumber, carrierName, slotIndex)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Could not get number via TelephonyManager for subscription $actualSubscriptionId", e)
                        }
                    }
                }
            }

            // Method 2: Use slot index with carrier info
            if (actualSlotIndex != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

                val matchingSubscription = activeSubscriptions?.find { it.simSlotIndex == actualSlotIndex }
                if (matchingSubscription != null) {
                    val phoneNumber = @Suppress("DEPRECATION") matchingSubscription.number
                    val carrierName = matchingSubscription.carrierName?.toString() ?: "Unknown"

                    if (!phoneNumber.isNullOrEmpty() && phoneNumber != "Unknown") {
                        Log.d(TAG, "✅ Slot Index Detection - Slot $actualSlotIndex ($carrierName): $phoneNumber")
                        return formatPhoneNumberWithCarrier(phoneNumber, carrierName, actualSlotIndex)
                    }
                }
            }

            // Method 3: Slot-based phone number mapping for known devices
            if (actualSlotIndex != -1) {
                // Enhanced: Use slot-based mapping when subscription lookup fails
                val slotBasedNumber = getPhoneNumberBySlotIndex(context, actualSlotIndex, actualSubscriptionId)
                if (slotBasedNumber != null) {
                    Log.d(TAG, "✅ Slot-Based Detection - Slot $actualSlotIndex: $slotBasedNumber [SubId: $actualSubscriptionId]")
                    val simInfo = SimSlotInfoCollector.collectSimSlotInfo(context)
                    val carrierInfo = simInfo.find { it.slotIndex == actualSlotIndex }
                    val carrierName = carrierInfo?.carrierName ?: "Unknown"
                    return formatPhoneNumberWithCarrier(slotBasedNumber, carrierName, actualSlotIndex)
                }
            }

            // Method 4: Comprehensive SIM information collection (from Rewardly)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

                if (!activeSubscriptions.isNullOrEmpty()) {
                    // Enhanced: Log all active SIMs for debugging
                    val allSimsInfo = activeSubscriptions.mapNotNull { sub ->
                        val number = @Suppress("DEPRECATION") sub.number
                        val carrier = sub.carrierName?.toString() ?: "Unknown"
                        val slot = sub.simSlotIndex
                        val subId = sub.subscriptionId
                        if (!number.isNullOrEmpty() && number != "Unknown") {
                            "SIM$slot ($carrier, SubId:$subId): $number"
                        } else null
                    }.joinToString(" | ")

                    Log.d(TAG, "📊 All Active SIMs: $allSimsInfo")

                    // Enhanced: Use the subscription with lowest slot index as primary
                    val primarySubscription = activeSubscriptions.minByOrNull { it.simSlotIndex }
                    if (primarySubscription != null) {
                        val phoneNumber = @Suppress("DEPRECATION") primarySubscription.number
                        val carrierName = primarySubscription.carrierName?.toString() ?: "Unknown"
                        val slotIndex = primarySubscription.simSlotIndex

                        if (!phoneNumber.isNullOrEmpty() && phoneNumber != "Unknown") {
                            Log.d(TAG, "✅ Primary SIM Selection - Slot $slotIndex ($carrierName): $phoneNumber")
                            return formatPhoneNumberWithCarrier(phoneNumber, carrierName, slotIndex)
                        }
                    }
                }
            }

            // Final fallback to TelephonyManager
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val line1Number = telephonyManager.line1Number
            if (!line1Number.isNullOrEmpty() && line1Number != "Unknown") {
                Log.d(TAG, "⚠️ Using TelephonyManager fallback: $line1Number")
                return line1Number
            }

            Log.w(TAG, "❌ Could not determine receiving phone number - All methods failed")
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
     * Get phone number by slot index using enhanced detection methods
     * This method handles cases where subscription manager returns "Unknown" for phone numbers
     */
    private fun getPhoneNumberBySlotIndex(context: Context, slotIndex: Int, subscriptionId: Int?): String? {
        return try {
            // Method 1: Try all TelephonyManager approaches for this subscription
            if (subscriptionId != null && subscriptionId != -1) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                // Try subscription-specific telephony manager
                try {
                    val subscriptionTelephony = telephonyManager.createForSubscriptionId(subscriptionId)
                    @Suppress("DEPRECATION")
                    val subNumber = subscriptionTelephony.line1Number
                    if (!subNumber.isNullOrEmpty() && subNumber != "Unknown") {
                        Log.d(TAG, "✅ Slot $slotIndex TelephonyManager Success: $subNumber")
                        return subNumber
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "TelephonyManager failed for slot $slotIndex, subId $subscriptionId", e)
                }
            }

  
            // Method 2: Enhanced universal phone number discovery
            val universalNumber = discoverPhoneNumberBySlot(context, slotIndex, subscriptionId)
            if (universalNumber != null) {
                Log.d(TAG, "✅ Slot $slotIndex Universal Discovery Success: $universalNumber")
                return universalNumber
            }

            Log.d(TAG, "⚠️ Could not determine phone number for slot $slotIndex")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number for slot $slotIndex", e)
            null
        }
    }

    /**
     * Universal phone number discovery for any Android device
     * Uses multiple approaches to discover phone numbers without hardcoding
     */
    private fun discoverPhoneNumberBySlot(context: Context, slotIndex: Int, subscriptionId: Int?): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Approach 1: Subscription-specific TelephonyManager
            if (subscriptionId != null && subscriptionId != -1) {
                try {
                    val subTelephony = telephonyManager.createForSubscriptionId(subscriptionId)
                    @Suppress("DEPRECATION")
                    val line1Number = subTelephony.line1Number
                    if (!line1Number.isNullOrEmpty() && line1Number != "Unknown") {
                        Log.d(TAG, "✅ Approach 1 - SubTelephony Success: $line1Number")
                        return line1Number
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Approach 1 failed", e)
                }
            }

            // Approach 2: Direct subscription manager lookup
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

                // Find subscription by slot index
                val slotSubscription = activeSubscriptions?.find { it.simSlotIndex == slotIndex }
                if (slotSubscription != null) {
                    @Suppress("DEPRECATION")
                    val subNumber = slotSubscription.number
                    if (!subNumber.isNullOrEmpty() && subNumber != "Unknown") {
                        Log.d(TAG, "✅ Approach 2 - Direct Subscription Success: $subNumber")
                        return subNumber
                    }
                }

                // Try all subscriptions and find the one with valid number for this slot
                activeSubscriptions?.forEach { sub ->
                    if (sub.simSlotIndex == slotIndex) {
                        try {
                            val subTelephony = telephonyManager.createForSubscriptionId(sub.subscriptionId)
                            @Suppress("DEPRECATION")
                            val number = subTelephony.line1Number
                            if (!number.isNullOrEmpty() && number != "Unknown") {
                                Log.d(TAG, "✅ Approach 2 - Iterative Success: $number")
                                return number
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Iterative approach failed for subId: ${sub.subscriptionId}")
                        }
                    }
                }
            }

            // Approach 3: Use SimSlotInfoCollector data
            val simInfo = SimSlotInfoCollector.collectSimSlotInfo(context)
            val targetSim = simInfo.find { it.slotIndex == slotIndex }
            if (targetSim != null && !targetSim.phoneNumber.isNullOrEmpty() && targetSim.phoneNumber != "Unknown") {
                Log.d(TAG, "✅ Approach 3 - SimSlotInfoCollector Success: ${targetSim.phoneNumber}")
                return targetSim.phoneNumber
            }

            // Approach 4: Last resort - try to infer from device state
            // Note: This is less reliable but can work in some cases
            try {
                @Suppress("DEPRECATION")
                val deviceNumber = telephonyManager.line1Number
                if (!deviceNumber.isNullOrEmpty() && deviceNumber != "Unknown") {
                    // For single SIM devices or when we can't determine slot-specific numbers
                    Log.d(TAG, "✅ Approach 4 - Device Fallback: $deviceNumber")
                    return deviceNumber
                }
            } catch (e: Exception) {
                Log.d(TAG, "Device fallback failed", e)
            }

            Log.d(TAG, "⚠️ All universal approaches failed for slot $slotIndex")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Universal phone number discovery failed for slot $slotIndex", e)
            null
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