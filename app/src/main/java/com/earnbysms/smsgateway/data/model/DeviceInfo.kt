package com.earnbysms.smsgateway.data.model

/**
 * Data model for device information
 */
data class DeviceInfo(
    val deviceId: String,
    val phoneNumber: String,
    val simSlots: List<SimSlotInfo>,
    val batteryLevel: Int,
    val deviceStatus: String,
    val deviceBrandInfo: DeviceBrandInfo
)

/**
 * Data model for SIM slot information
 */
data class SimSlotInfo(
    val slotIndex: Int,
    val carrierName: String,
    val phoneNumber: String,
    val operatorName: String,
    val signalStatus: String
)

/**
 * Data model for device brand information
 */
data class DeviceBrandInfo(
    val brand: String,
    val model: String,
    val product: String,
    val board: String,
    val device: String,
    val manufacturer: String,
    val signalStatus: String = ""
)

/**
 * Data model for SMS message
 */
data class SmsMessage(
    val deviceId: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val recipient: String,
    val slotIndex: Int? = null,
    val subscriptionId: Int? = null,
    val androidVersion: String,
    val manufacturer: String,
    val model: String
)

/**
 * Data model for heartbeat data
 */
data class HeartbeatData(
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int,
    val isCharging: Boolean,
    val signalStrength: Int?,
    val networkType: String?,
    val activeSimSlots: Int,
    val totalSimSlots: Int,
    val uptime: Long,
    val appVersion: String,
    val lastSmsTimestamp: Long?,
    val pendingSmsCount: Int,
    val failedSmsCount: Int
)