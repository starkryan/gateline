package com.earnbysms.smsgateway.utils

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.SignalStrength
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoCdma
import android.util.Log

/**
 * Comprehensive SIM slot information data class.
 * Based on Rewardly project with enhanced signal strength detection.
 */
data class SimSlotInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val carrierName: String,
    val phoneNumber: String,
    val signalStatus: String? = null,
    val networkType: String? = null,
    val isActive: Boolean = true
)

/**
 * Utility class for collecting comprehensive SIM slot information.
 * Based on Rewardly project implementation with enhanced dual-SIM support.
 */
object SimSlotInfoCollector {
    private const val TAG = "SimSlotInfoCollector"

    /**
     * Collect comprehensive information about all active SIM slots.
     * Returns detailed SIM slot information with signal strength and carrier details.
     */
    fun collectSimSlotInfo(context: Context): List<SimSlotInfo> {
        val simSlots = mutableListOf<SimSlotInfo>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

                activeSubscriptions?.forEach { subscriptionInfo ->
                    val signalStatus = getSignalStrengthForSubscription(context, subscriptionInfo.subscriptionId)
                    val networkType = getNetworkTypeForSubscription(context, subscriptionInfo.subscriptionId)

                    simSlots.add(
                        SimSlotInfo(
                            slotIndex = subscriptionInfo.simSlotIndex,
                            subscriptionId = subscriptionInfo.subscriptionId,
                            carrierName = subscriptionInfo.carrierName?.toString() ?: "Unknown",
                            phoneNumber = getPhoneNumberFromSubscriptionInfo(subscriptionInfo),
                            signalStatus = signalStatus,
                            networkType = networkType,
                            isActive = true
                        )
                    )
                }

                Log.d(TAG, "📊 Collected ${simSlots.size} active SIM slots")
                logSimSlotDetails(simSlots)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "🔒 Permission denied for subscription info", e)
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error collecting SIM slot info", e)
        }

        return simSlots
    }

    /**
     * Get signal strength for a specific subscription ID.
     * Uses multiple methods for comprehensive signal detection.
     */
    private fun getSignalStrengthForSubscription(context: Context, subscriptionId: Int): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Method 1: Use SignalStrength.getLevel() for simplified signal level
            @Suppress("DEPRECATION")
            val signalStrength = telephonyManager.signalStrength
            if (signalStrength != null) {
                val level = signalStrength.level // Returns 0-4 (bad to great)
                val quality = when {
                    level >= 4 -> "Excellent"
                    level >= 3 -> "Good"
                    level >= 2 -> "Fair"
                    level >= 1 -> "Poor"
                    else -> "Very Poor"
                }
                Log.d(TAG, "📶 Cellular Signal Level - Subscription $subscriptionId: $level ($quality)")
                return quality
            }

            // Method 2: Use detailed CellInfo for precise measurements
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val allCellInfo = telephonyManager.allCellInfo
                if (allCellInfo != null && allCellInfo.isNotEmpty()) {
                    for (cellInfo in allCellInfo) {
                        when (cellInfo) {
                            is CellInfoLte -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val asuLevel = signalStrength.asuLevel
                                val quality = getSignalQuality(dbm, "LTE")
                                Log.d(TAG, "📶 LTE Signal Detail - Subscription $subscriptionId: $dbm dBm ($asuLevel ASU) - $quality")
                                return quality
                            }
                            is CellInfoGsm -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val asuLevel = signalStrength.asuLevel
                                val quality = getSignalQuality(dbm, "GSM")
                                Log.d(TAG, "📶 GSM Signal Detail - Subscription $subscriptionId: $dbm dBm ($asuLevel ASU) - $quality")
                                return quality
                            }
                            is CellInfoWcdma -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val asuLevel = signalStrength.asuLevel
                                val quality = getSignalQuality(dbm, "WCDMA")
                                Log.d(TAG, "📶 WCDMA Signal Detail - Subscription $subscriptionId: $dbm dBm ($asuLevel ASU) - $quality")
                                return quality
                            }
                            is CellInfoCdma -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val asuLevel = signalStrength.asuLevel
                                val quality = getSignalQuality(dbm, "CDMA")
                                Log.d(TAG, "📶 CDMA Signal Detail - Subscription $subscriptionId: $dbm dBm ($asuLevel ASU) - $quality")
                                return quality
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "⚠️ No signal information available for subscription $subscriptionId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signal strength for subscription $subscriptionId", e)
            null
        }
    }

    /**
     * Get network type for a specific subscription ID.
     */
    private fun getNetworkTypeForSubscription(context: Context, subscriptionId: Int): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Use subscription-specific telephony manager for network type
            val subscriptionTelephony = telephonyManager.createForSubscriptionId(subscriptionId)
            @Suppress("DEPRECATION")
            val networkType = when (subscriptionTelephony.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSDPA -> "3G HSPA"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                else -> "Unknown"
            }

            Log.d(TAG, "📱 Network Type - Subscription $subscriptionId: $networkType")
            networkType
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type for subscription $subscriptionId", e)
            null
        }
    }

    /**
     * Get phone number from subscription info with fallbacks.
     */
    private fun getPhoneNumberFromSubscriptionInfo(subscriptionInfo: SubscriptionInfo): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, use subscription info number if available
                @Suppress("DEPRECATION")
                val number = subscriptionInfo.number
                if (!number.isNullOrEmpty()) number else "Unknown"
            } else {
                // For older Android versions, use deprecated API
                @Suppress("DEPRECATION")
                subscriptionInfo.number ?: "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number from subscription info", e)
            "Unknown"
        }
    }

    /**
     * Convert signal strength (dBm) to quality description.
     * Network type specific thresholds for accurate quality assessment.
     */
    private fun getSignalQuality(dbm: Int, networkType: String): String {
        return when (networkType) {
            "LTE" -> {
                when {
                    dbm >= -80 -> "Excellent"
                    dbm >= -90 -> "Good"
                    dbm >= -100 -> "Fair"
                    dbm >= -110 -> "Poor"
                    else -> "Very Poor"
                }
            }
            "5G NR" -> {
                when {
                    dbm >= -75 -> "Excellent"
                    dbm >= -85 -> "Good"
                    dbm >= -95 -> "Fair"
                    dbm >= -105 -> "Poor"
                    else -> "Very Poor"
                }
            }
            "WCDMA" -> {
                when {
                    dbm >= -70 -> "Excellent"
                    dbm >= -80 -> "Good"
                    dbm >= -90 -> "Fair"
                    dbm >= -100 -> "Poor"
                    else -> "Very Poor"
                }
            }
            "GSM", "CDMA" -> {
                when {
                    dbm >= -70 -> "Excellent"
                    dbm >= -85 -> "Good"
                    dbm >= -100 -> "Fair"
                    dbm >= -110 -> "Poor"
                    else -> "Very Poor"
                }
            }
            else -> {
                when {
                    dbm >= -80 -> "Excellent"
                    dbm >= -90 -> "Good"
                    dbm >= -100 -> "Fair"
                    dbm >= -110 -> "Poor"
                    else -> "Very Poor"
                }
            }
        }
    }

    /**
     * Log comprehensive SIM slot details for debugging.
     */
    private fun logSimSlotDetails(simSlots: List<SimSlotInfo>) {
        Log.d(TAG, "=== SIM SLOT DETAILS ===")
        simSlots.forEachIndexed { index, sim ->
            Log.d(TAG, "SIM ${index + 1}:")
            Log.d(TAG, "  Slot Index: ${sim.slotIndex}")
            Log.d(TAG, "  Subscription ID: ${sim.subscriptionId}")
            Log.d(TAG, "  Carrier: ${sim.carrierName}")
            Log.d(TAG, "  Phone Number: ${sim.phoneNumber}")
            Log.d(TAG, "  Signal: ${sim.signalStatus ?: "N/A"}")
            Log.d(TAG, "  Network: ${sim.networkType ?: "N/A"}")
            Log.d(TAG, "  Active: ${sim.isActive}")
        }
        Log.d(TAG, "===================")
    }

    /**
     * Get formatted SIM information string for logging and debugging.
     */
    fun getFormattedSimInfo(context: Context): String {
        val simSlots = collectSimSlotInfo(context)
        return simSlots.map { sim ->
            "SIM${sim.slotIndex} (${sim.carrierName}): ${sim.phoneNumber} [${sim.signalStatus ?: "N/A"}]"
        }.joinToString(" | ")
    }

    /**
     * Gets a unique device identifier for the SMS gateway.
     * Uses multiple fallback methods to ensure device identification.
     */
    fun getDeviceId(context: Context): String {
        // Try multiple methods to get a reliable device ID
        return try {
            // Method 1: Android ID
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.w(TAG, "Could not get Android ID", e)
            // Method 2: Fallback to timestamp-based ID
            "device_${System.currentTimeMillis()}"
        }
    }

    /**
     * Gets the primary phone number for the device.
     * Checks multiple sources for phone number detection.
     */
    fun getPhoneNumber(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return try {
            // Method 1: Direct from TelephonyManager
            telephonyManager.line1Number?.takeIf { it.isNotBlank() && it != "null" }
                ?: try {
                    // Method 2: From subscription info
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    subscriptionManager.activeSubscriptionInfoList
                        .firstNotNullOfOrNull { subscription ->
                            getPhoneNumberFromSubscriptionInfo(subscription).takeIf {
                                it.isNotBlank() && it != "Unknown" && it != "null"
                            }
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get phone number from subscriptions", e)
                    null
                }
                ?: "Unknown"
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read phone number", e)
            "Unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Error getting phone number", e)
            "Unknown"
        }
    }

    /**
     * Formats signal strength information into a human-readable string.
     */
    fun formatSignalStrength(level: Int, dbm: Int, networkType: String): String {
        val signalQuality = getSignalQuality(dbm, networkType)
        return "Level $level ($dbm dBm) - $signalQuality"
    }

    companion object {
        private const val TAG = "DeviceUtils"
    }
}