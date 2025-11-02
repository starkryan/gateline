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
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.util.Log
import com.earnbysms.smsgateway.data.model.SimSlotInfo

/**
 * Utility class for collecting comprehensive SIM slot information
 */
object SimSlotInfoCollector {

    private const val TAG = "SimSlotInfoCollector"

    /**
     * Collects SIM slot information for all active subscriptions
     */
    fun collectSimSlotInfo(context: Context): List<SimSlotInfo> {
        val simSlots = mutableListOf<SimSlotInfo>()

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
                Log.d(TAG, "Found ${subscriptionInfoList?.size ?: 0} active subscriptions")

                subscriptionInfoList?.forEach { subscriptionInfo ->
                    val simSlot = createSimSlotInfo(context, subscriptionInfo, telephonyManager)
                    if (simSlot != null) {
                        simSlots.add(simSlot)
                    }
                }
            } else {
                // Fallback for older Android versions
                Log.d(TAG, "Using fallback method for Android < 5.1")
                val fallbackSimSlot = createFallbackSimSlotInfo(telephonyManager)
                if (fallbackSimSlot != null) {
                    simSlots.add(fallbackSimSlot)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied accessing SIM information", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting SIM slot information", e)
        }

        Log.d(TAG, "Collected ${simSlots.size} SIM slot entries")
        return simSlots
    }

    /**
     * Creates SimSlotInfo from SubscriptionInfo
     */
    private fun createSimSlotInfo(
        context: Context,
        subscriptionInfo: SubscriptionInfo,
        telephonyManager: TelephonyManager
    ): SimSlotInfo? {
        return try {
            val subscriptionId = subscriptionInfo.subscriptionId
            val slotIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subscriptionInfo.simSlotIndex
            } else {
                0 // Fallback for older versions
            }

            // Get signal strength
            val signalStrength = getSignalStrength(context, subscriptionId)
            val signalStatus = getSignalStatus(signalStrength)

            // Get network type
            val networkType = getNetworkType(context, subscriptionId)

            // Get phone number
            val phoneNumber = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subscriptionInfo.number?.takeIf { it.isNotBlank() }
                } else {
                    telephonyManager.line1Number?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get phone number", e)
                null
            }

            SimSlotInfo(
                slotIndex = slotIndex,
                carrierName = subscriptionInfo.carrierName?.toString() ?: "",
                phoneNumber = phoneNumber ?: "",
                operatorName = subscriptionInfo.displayName?.toString() ?: "",
                signalStatus = signalStatus ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SimSlotInfo for subscription ${subscriptionInfo.subscriptionId}", e)
            null
        }
    }

    /**
     * Creates fallback SimSlotInfo for older Android versions
     */
    private fun createFallbackSimSlotInfo(telephonyManager: TelephonyManager): SimSlotInfo? {
        return try {
            val carrierName = telephonyManager.simOperatorName?.takeIf { it.isNotBlank() }
            val phoneNumber = telephonyManager.line1Number?.takeIf { it.isNotBlank() }
            val simOperator = telephonyManager.simOperator?.takeIf { it.isNotBlank() }

            // Parse MCC and MNC from simOperator
            var mcc: Int? = null
            var mnc: Int? = null
            simOperator?.let { operator ->
                if (operator.length >= 5) {
                    mcc = operator.substring(0, 3).toIntOrNull()
                    mnc = operator.substring(3).toIntOrNull()
                }
            }

            SimSlotInfo(
                slotIndex = 0,
                carrierName = carrierName ?: "",
                phoneNumber = phoneNumber ?: "",
                operatorName = carrierName ?: "",
                signalStatus = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback SimSlotInfo", e)
            null
        }
    }

    /**
     * Gets signal strength for a specific subscription
     */
    private fun getSignalStrength(context: Context, subscriptionId: Int): Int? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Create a custom PhoneStateListener to get signal strength
            var signalStrengthValue: Int? = null
            val listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrengths: SignalStrength) {
                    signalStrengthValue = signalStrengths.level
                }
            }

            // Register listener temporarily (this is a simplified approach)
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

            // Wait a moment for the listener to be called
            Thread.sleep(100)

            // Unregister listener
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)

            signalStrengthValue
        } catch (e: Exception) {
            Log.w(TAG, "Could not get signal strength for subscription $subscriptionId", e)
            null
        }
    }

    /**
     * Gets signal status from signal strength
     */
    private fun getSignalStatus(signalStrength: Int?): String? {
        return signalStrength?.let { strength ->
            when {
                strength >= 4 -> "Excellent"
                strength >= 3 -> "Good"
                strength >= 2 -> "Fair"
                strength >= 1 -> "Poor"
                else -> "No Signal"
            }
        }
    }

    /**
     * Gets network type for a specific subscription
     */
    private fun getNetworkType(context: Context, subscriptionId: Int): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (telephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO Rev. 0"
                TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO Rev. A"
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO Rev. B"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
                TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
                TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get network type for subscription $subscriptionId", e)
            null
        }
    }

    /**
     * Gets data network type
     */
    private fun getDataNetworkType(telephonyManager: TelephonyManager): String? {
        return try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO Rev. 0"
                TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO Rev. A"
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO Rev. B"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
                TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
                TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets network type from TelephonyManager (fallback method)
     */
    private fun getNetworkTypeFromTelephonyManager(telephonyManager: TelephonyManager): String? {
        return try {
            when (telephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets SIM state from SubscriptionInfo
     */
  
  
    /**
     * Gets formatted SIM information string for logging
     */
    fun getFormattedSimInfo(context: Context): String {
        val simSlots = collectSimSlotInfo(context)
        return simSlots.map { sim ->
            val carrierName = if (sim.carrierName.isNotBlank()) sim.carrierName else sim.operatorName
            val phoneNumber = if (sim.phoneNumber.isNotBlank()) sim.phoneNumber else "Unknown"
            "SIM${sim.slotIndex} ($carrierName): $phoneNumber [${sim.signalStatus ?: "N/A"}]"
        }.joinToString(" | ")
    }
}