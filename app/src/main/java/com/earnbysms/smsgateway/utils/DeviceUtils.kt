package com.earnbysms.smsgateway.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
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
 * Device utility functions for SMS gateway operations.
 */
object DeviceUtils {

    private const val TAG = "DeviceUtils"

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
                        ?.firstNotNullOfOrNull { subscription ->
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

    /**
     * Gets signal quality from dbm and network type
     */
    private fun getSignalQuality(dbm: Int, networkType: String): String {
        return when {
            dbm >= -50 -> "Excellent"
            dbm >= -60 -> "Good"
            dbm >= -70 -> "Fair"
            dbm >= -85 -> "Poor"
            else -> "No Signal"
        }
    }

    /**
     * Gets phone number from subscription info
     */
    private fun getPhoneNumberFromSubscriptionInfo(subscriptionInfo: SubscriptionInfo): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subscriptionInfo.number?.takeIf { it.isNotBlank() } ?: "Unknown"
            } else {
                // Fallback for older versions
                "Unknown"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract phone number from subscription info", e)
            "Unknown"
        }
    }
}