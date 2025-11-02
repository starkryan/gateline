package com.earnbysms.smsgateway.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.UUID

/**
 * Utility class for generating persistent device identifiers that survive app uninstallation.
 *
 * Based on Rewardly project implementation - provides reliable device identification
 * for SMS gateway operations with multiple fallback strategies.
 *
 * Persistence Strategy:
 * 1. Android ID (persists across app reinstalls)
 * 2. Installation-specific UUID (persists across app updates)
 * 3. Legacy UUID (emergency fallback)
 */
object PersistentDeviceId {
    private const val TAG = "PersistentDeviceId"
    private const val PREFS_NAME = "persistent_device_id"
    private const val KEY_LEGACY_ID = "legacy_device_id"
    private const val KEY_ANDROID_ID = "android_id"
    private const val KEY_INSTALLATION_ID = "installation_id"

    /**
     * Get the most persistent device ID available.
     * Priority order based on reliability and persistence:
     * 1. Android ID (persists across app reinstalls)
     * 2. Installation-specific UUID (persists across app updates)
     * 3. Legacy UUID (fallback only)
     */
    fun getDeviceId(context: Context): String {
        return try {
            // Primary: Use Android ID - most persistent option
            getAndroidId(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Android ID, using fallback", e)
            // Fallback: Use installation-specific UUID
            getInstallationId(context)
        }
    }

    /**
     * Get Android ID - the most persistent identifier available.
     * Persists across app reinstalls as long as package name and signing key remain the same.
     * This is the preferred method for SMS gateway device identification.
     */
    private fun getAndroidId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return when {
            androidId == null -> {
                Log.w(TAG, "Android ID is null, using installation ID")
                getInstallationId(context)
            }
            androidId == "9774d56d682e549c" -> {
                // This is the default Android ID for emulators and some devices
                Log.w(TAG, "Android ID is default value, using installation ID")
                getInstallationId(context)
            }
            androidId.isBlank() -> {
                Log.w(TAG, "Android ID is blank, using installation ID")
                getInstallationId(context)
            }
            else -> {
                // Valid Android ID - this will persist across app reinstalls
                Log.d(TAG, "✅ Using persistent Android ID: $androidId")

                // Store the Android ID in prefs for easy access and debugging
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val storedAndroidId = prefs.getString(KEY_ANDROID_ID, null)

                if (storedAndroidId != androidId) {
                    prefs.edit()
                        .putString(KEY_ANDROID_ID, androidId)
                        .apply()
                    Log.i(TAG, "Stored new Android ID: $androidId")
                }

                androidId
            }
        }
    }

    /**
     * Get installation-specific UUID.
     * This persists across app updates but changes on reinstall.
     * Used as fallback when Android ID is not available.
     */
    private fun getInstallationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var installationId = prefs.getString(KEY_INSTALLATION_ID, null)

        if (installationId == null) {
            installationId = "install_" + UUID.randomUUID().toString()
            prefs.edit()
                .putString(KEY_INSTALLATION_ID, installationId)
                .apply()
            Log.i(TAG, "Generated new installation ID: $installationId")
        } else {
            Log.d(TAG, "Using existing installation ID: $installationId")
        }

        return installationId
    }

    /**
     * Get legacy device ID from SharedPreferences.
     * This was used in older versions and is maintained for compatibility.
     */
    private fun getLegacyDeviceId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LEGACY_ID, null)
    }

    /**
     * Check if the current device ID is persistent (Android ID).
     * Useful for determining if device ID will survive app reinstall.
     */
    fun isPersistentId(context: Context): Boolean {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            androidId != null &&
            androidId != "9774d56d682e549c" &&
            androidId.isNotBlank() &&
            getDeviceId(context) == androidId
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get comprehensive device ID information for debugging.
     * Includes all ID types and persistence status.
     */
    fun getDeviceInfo(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val androidId = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        } catch (e: Exception) {
            null
        }

        return mapOf(
            "current_device_id" to getDeviceId(context),
            "android_id" to (androidId ?: "null"),
            "stored_android_id" to (prefs.getString(KEY_ANDROID_ID, "null") ?: "null"),
            "installation_id" to (prefs.getString(KEY_INSTALLATION_ID, "null") ?: "null"),
            "legacy_id" to (prefs.getString(KEY_LEGACY_ID, "null") ?: "null"),
            "is_persistent" to isPersistentId(context).toString()
        )
    }

    /**
     * Clear all stored device IDs (for testing or reset).
     * WARNING: This will change the device ID on next app launch.
     */
    fun clearDeviceIds(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.i(TAG, "⚠️ Cleared all stored device IDs - new ID will be generated on next launch")
    }

    /**
     * Log comprehensive device ID information for debugging SMS routing issues.
     */
    fun logDeviceInfo(context: Context) {
        val deviceInfo = getDeviceInfo(context)
        Log.i(TAG, "=== DEVICE ID INFORMATION ===")
        deviceInfo.forEach { (key, value) ->
            Log.i(TAG, "$key: $value")
        }
        Log.i(TAG, "=============================")
    }
}