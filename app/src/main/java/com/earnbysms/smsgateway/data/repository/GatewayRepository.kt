package com.earnbysms.smsgateway.data.repository

import com.earnbysms.smsgateway.data.model.DeviceInfo
import com.earnbysms.smsgateway.data.model.SmsMessage
import com.earnbysms.smsgateway.data.remote.api.GatewayApi
import com.earnbysms.smsgateway.data.remote.api.HeartbeatData
import com.earnbysms.smsgateway.utils.DeviceUtils
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for handling GOIP gateway operations
 */
class GatewayRepository(
    private val gatewayApi: GatewayApi,
    private val context: Context,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "GatewayRepository"
    }

    /**
     * Register device with GOIP server
     */
    suspend fun registerDevice(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val deviceInfo = collectDeviceInfo()
            val response = gatewayApi.registerDevice(deviceInfo)

            if (response.success) {
                Log.d(TAG, "Device registered successfully: $response")
                Result.success(true)
            } else {
                Log.e(TAG, "Device registration failed: ${response.message}")
                Result.failure(Exception("Registration failed: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during device registration", e)
            Result.failure(e)
        }
    }

    /**
     * Forward SMS to GOIP server
     */
    suspend fun forwardSms(
        sender: String,
        body: String,
        subscriptionId: Int? = null,
        simSlot: Int? = null,
        receivingNumber: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val recipient = receivingNumber ?: getPhoneNumberForSIMSlot(simSlot)

            val smsMessage = SmsMessage(
                deviceId = DeviceUtils.getDeviceId(context),
                sender = sender,
                message = body,
                timestamp = System.currentTimeMillis(),
                recipient = recipient,
                slotIndex = simSlot,
                subscriptionId = subscriptionId,
                androidVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL
            )

            val response = gatewayApi.forwardSms(smsMessage)

            if (response.success) {
                Log.d(TAG, "SMS forwarded successfully: $sender -> $recipient")
                Result.success(true)
            } else {
                Log.e(TAG, "SMS forwarding failed: ${response.message}")
                Result.failure(Exception("SMS forwarding failed: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during SMS forwarding", e)
            Result.failure(e)
        }
    }

    /**
     * Send heartbeat to GOIP server
     */
    suspend fun sendHeartbeat(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val heartbeatData = HeartbeatData(
                deviceId = DeviceUtils.getDeviceId(context),
                batteryLevel = getBatteryLevel(),
                simSlots = getSimSlotsInfo(),
                deviceBrandInfo = getDeviceBrandInfo()
            )

            val response = gatewayApi.sendHeartbeat(heartbeatData)

            if (response.success) {
                Log.d(TAG, "Heartbeat sent successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Heartbeat failed: ${response.message}")
                Result.failure(Exception("Heartbeat failed: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
            Result.failure(e)
        }
    }

    /**
     * Collect comprehensive device information
     */
    private fun collectDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = DeviceUtils.getDeviceId(context),
            phoneNumber = getPhoneNumber(),
            simSlots = getSimSlotsInfo(),
            batteryLevel = getBatteryLevel(),
            deviceStatus = "online",
            deviceBrandInfo = getDeviceBrandInfo()
        )
    }

    /**
     * Get phone number for device
     */
    private fun getPhoneNumber(): String {
        return DeviceUtils.getPhoneNumber(context)
    }

    /**
     * Get SIM slots information
     */
    private fun getSimSlotsInfo(): List<com.earnbysms.smsgateway.data.model.SimSlotInfo> {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val simSlots = mutableListOf<com.earnbysms.smsgateway.data.model.SimSlotInfo>()

            // Get all active subscription IDs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
                Log.d(TAG, "Found ${activeSubscriptionInfoList?.size ?: 0} active subscriptions")

                activeSubscriptionInfoList?.forEachIndexed { index, subscriptionInfo ->
                    try {
                        // Get TelephonyManager for this specific subscription
                        val subscriptionTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                        // Try to get phone number from multiple sources
                        val phoneNumber = try {
                            when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                                    // Method 1: Try subscriptionInfo.number (Android 10+)
                                    val number1 = subscriptionInfo.number?.toString()
                                    if (!number1.isNullOrEmpty()) {
                                        Log.d(TAG, "Got phone number from subscriptionInfo.number: $number1")
                                        number1
                                    } else {
                                        // Fallback to other methods
                                        getPhoneNumberFromMultipleSources(subscriptionInfo.subscriptionId, subscriptionInfo.displayName?.toString())
                                    }
                                }
                                else -> {
                                    // Method 2: Use SubscriptionManager.getPhoneNumber (Android 5.1+)
                                    try {
                                        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                                        val number2 = subscriptionManager.getPhoneNumber(subscriptionInfo.subscriptionId)
                                        if (!number2.isNullOrEmpty()) {
                                            Log.d(TAG, "Got phone number from SubscriptionManager.getPhoneNumber: $number2")
                                            number2 ?: ""
                                        } else {
                                            // Method 3: Try manual SIM detection
                                            getPhoneNumberFromMultipleSources(subscriptionInfo.subscriptionId, subscriptionInfo.displayName?.toString())
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Could not get phone number via SubscriptionManager.getPhoneNumber", e)
                                        // Method 4: Try manual SIM detection
                                        getPhoneNumberFromMultipleSources(subscriptionInfo.subscriptionId, subscriptionInfo.displayName?.toString())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not get phone number for subscription ${subscriptionInfo.subscriptionId}", e)
                            // Method 5: Manual fallback
                            getPhoneNumberFromMultipleSources(subscriptionInfo.subscriptionId, subscriptionInfo.displayName?.toString())
                        }

                        val simSlot = com.earnbysms.smsgateway.data.model.SimSlotInfo(
                            slotIndex = subscriptionInfo.simSlotIndex ?: index,
                            carrierName = subscriptionInfo.carrierName?.toString() ?: "",
                            phoneNumber = phoneNumber,
                            operatorName = subscriptionInfo.displayName?.toString() ?: "",
                            signalStatus = getSignalStatus()
                        )
                        simSlots.add(simSlot)

                        Log.d(TAG, "Added SIM slot ${simSlot.slotIndex}: ${simSlot.carrierName} (${simSlot.phoneNumber})")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not process subscription at index $index", e)
                    }
                }
            } else {
                // Fallback for older Android versions - only primary SIM
                Log.d(TAG, "Using fallback for older Android version")
                val primarySIM = com.earnbysms.smsgateway.data.model.SimSlotInfo(
                    slotIndex = 0,
                    carrierName = telephonyManager.networkOperatorName ?: "",
                    phoneNumber = getPhoneNumber(),
                    operatorName = telephonyManager.simOperatorName ?: "",
                    signalStatus = getSignalStatus()
                )
                simSlots.add(primarySIM)
            }

            if (simSlots.isEmpty()) {
                Log.w(TAG, "No SIM slots found, adding empty slot 0")
                // Add empty slot to ensure at least one SIM is reported
                val emptySIM = com.earnbysms.smsgateway.data.model.SimSlotInfo(
                    slotIndex = 0,
                    carrierName = "Unknown",
                    phoneNumber = "",
                    operatorName = "",
                    signalStatus = "Unknown"
                )
                simSlots.add(emptySIM)
            }

            simSlots
        } catch (e: Exception) {
            Log.w(TAG, "Could not get SIM slots info", e)
            // Return empty list as last resort
            emptyList()
        }
    }

    /**
     * Get phone number using intent extras and SubscriptionManager like Rewardly app
     * This is the simplified and most reliable approach based on Rewardly app success
     */
    @Suppress("DEPRECATION")
    private fun getPhoneNumberFromMultipleSources(subscriptionId: Int, carrierName: String?): String {
        return try {
            // Method 1: Try SubscriptionManager.getPhoneNumber (Android 5.1+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    val number = subscriptionManager.getPhoneNumber(subscriptionId)
                    if (!number.isNullOrEmpty() && number != "Unknown") {
                        Log.d(TAG, "✅ Found phone number via SubscriptionManager.getPhoneNumber: $number")
                        return number
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "SubscriptionManager.getPhoneNumber failed", e)
                }
            }

            // Method 2: Try active subscription info
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                    val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
                    val targetSubscription = activeSubscriptionInfoList?.find { it.subscriptionId == subscriptionId }
                    if (targetSubscription?.number?.isNotEmpty() == true) {
                        val number = targetSubscription.number.toString()
                        Log.d(TAG, "✅ Found phone number via active subscription info: $number")
                        return number
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Active subscription info failed", e)
                }
            }

            // Method 3: Try DeviceUtils fallback
            try {
                val devicePhoneNumber = DeviceUtils.getPhoneNumber(context)
                if (!devicePhoneNumber.isNullOrEmpty() && devicePhoneNumber != "Unknown") {
                    Log.d(TAG, "Using DeviceUtils fallback: $devicePhoneNumber")
                    return devicePhoneNumber
                }
            } catch (e: Exception) {
                Log.d(TAG, "DeviceUtils fallback failed", e)
            }

            Log.d(TAG, "Could not determine phone number for sub $subscriptionId")
            "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number for sub $subscriptionId", e)
            "Unknown"
        }
    }

  

    /**
     * Get phone number for specific subscription slot (legacy method)
     */
    @Suppress("DEPRECATION")
    private fun getPhoneNumberForSlot(subscriptionId: Int): String {
        return getPhoneNumberFromMultipleSources(subscriptionId, null)
    }

    /**
     * Get battery level as percentage
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.w(TAG, "Could not get battery level", e)
            0
        }
    }

    /**
     * Get device brand information
     */
    private fun getDeviceBrandInfo(): com.earnbysms.smsgateway.data.model.DeviceBrandInfo {
        return com.earnbysms.smsgateway.data.model.DeviceBrandInfo(
            brand = Build.BRAND,
            model = Build.MODEL,
            product = Build.PRODUCT,
            board = Build.BOARD,
            device = Build.DEVICE,
            manufacturer = Build.MANUFACTURER,
            signalStatus = getSignalStatus()
        )
    }

    /**
     * Get phone number for specific SIM slot
     */
    private fun getPhoneNumberForSIMSlot(simSlot: Int?): String {
        return getPhoneNumber() // For simplicity, return primary number
    }

    /**
     * Get signal status with real dBm values
     */
    private fun getSignalStatus(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val signalStrength = telephonyManager.signalStrength
                    if (signalStrength != null) {
                        val level = signalStrength.level
                        val dbm = getSignalDbm(signalStrength)
                        val networkType = getNetworkType(telephonyManager)
                        DeviceUtils.formatSignalStrength(level, dbm, networkType)
                    } else {
                        "No signal"
                    }
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    try {
                        val signalStrength = getSignalStrengthLegacy(telephonyManager)
                        if (signalStrength != null) {
                            val dbm = signalStrength
                            val level = getSignalLevelFromDbm(dbm)
                            val networkType = getNetworkType(telephonyManager)
                            DeviceUtils.formatSignalStrength(level, dbm, networkType)
                        } else {
                            "No signal"
                        }
                    } catch (e: Exception) {
                        "Signal unavailable"
                    }
                }

                else -> {
                    val networkType = getNetworkType(telephonyManager)
                    val networkOperator = telephonyManager.networkOperatorName ?: "Unknown"
                    "$networkType ($networkOperator)"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get signal status", e)
            "Signal error"
        }
    }

    /**
     * Get signal strength in dBm for Android 10+
     */
    private fun getSignalDbm(signalStrength: android.telephony.SignalStrength): Int {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    try {
                        // Try alternative methods for getting signal strength on newer Android versions
                        when {
                            // Try getCellSignalStrengths() for Android 12+
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                                val cellSignalStrengths = signalStrength.cellSignalStrengths
                                if (cellSignalStrengths.isNotEmpty()) {
                                    val firstCell = cellSignalStrengths[0]
                                    when (firstCell) {
                                        is android.telephony.CellSignalStrengthLte -> firstCell.dbm
                                        is android.telephony.CellSignalStrengthGsm -> firstCell.dbm
                                        is android.telephony.CellSignalStrengthWcdma -> firstCell.dbm
                                        is android.telephony.CellSignalStrengthCdma -> firstCell.dbm
                                        else -> -999
                                    }
                                } else -999
                            }
                            // Fallback for older versions
                            else -> {
                                try {
                                    // Try reflection as last resort
                                    val method = signalStrength.javaClass.getMethod("getDbm")
                                    val result = method.invoke(signalStrength)
                                    when (result) {
                                        is IntArray -> if (result.isNotEmpty()) result[0] else -999
                                        is Int -> result
                                        else -> -999
                                    }
                                } catch (e: Exception) {
                                    Log.d(TAG, "getDbm method not available, using fallback", e)
                                    // Use alternative method: getLevel() as approximation
                                    signalStrength.level
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not get signal strength", e)
                        -999
                    }
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    try {
                        // For Android 10-11, use alternative approach
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                // This shouldn't happen due to when conditions, but just in case
                                -999
                            }
                            else -> {
                                try {
                                    val method = signalStrength.javaClass.getMethod("getDbm")
                                    method.invoke(signalStrength) as? Int ?: -999
                                } catch (e: Exception) {
                                    Log.d(TAG, "getDbm method not available on Android 10-11, using level", e)
                                    signalStrength.level
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not get signal strength on Android 10-11", e)
                        -999
                    }
                }

                else -> -999
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not get dBm value", e)
            -999
        }
    }

    /**
     * Get signal strength using reflection for Android 6-9
     */
    private fun getSignalStrengthLegacy(telephonyManager: TelephonyManager): Int? {
        return try {
            val method = telephonyManager.javaClass.getMethod("getSignalStrength")
            val signalStrength = method.invoke(telephonyManager)
            val dbmMethod = signalStrength?.javaClass?.getMethod("getDbm")
            dbmMethod?.invoke(signalStrength) as? Int
        } catch (e: Exception) {
            Log.d(TAG, "Legacy signal detection failed", e)
            null
        }
    }

    /**
     * Convert dBm to signal level (0-4)
     */
    private fun getSignalLevelFromDbm(dbm: Int): Int {
        return when {
            dbm >= -50 -> 4
            dbm >= -60 -> 3
            dbm >= -70 -> 2
            dbm >= -85 -> 1
            dbm >= -100 -> 0
            else -> 0
        }
    }

    /**
     * Get current network type with detailed information
     */
    private fun getNetworkType(telephonyManager: TelephonyManager): String {
        return try {
            val networkType = when (telephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
                else -> {
                    when (telephonyManager.networkType) {
                        19 -> "NR (5G)"
                        20 -> "NR (5G NSA)"
                        21 -> "NR (5G+ mmWave)"
                        else -> "Unknown"
                    }
                }
            }

            val isRoaming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager.isNetworkRoaming
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.isNetworkRoaming
            }

            if (isRoaming) {
                "$networkType [Roaming]"
            } else {
                networkType
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get network type", e)
            "Unknown Network"
        }
    }
}