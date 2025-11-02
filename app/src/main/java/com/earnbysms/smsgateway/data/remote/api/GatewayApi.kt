package com.earnbysms.smsgateway.data.remote.api

import com.earnbysms.smsgateway.data.model.SmsMessage
import com.earnbysms.smsgateway.data.model.DeviceInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * GOIP Gateway API interface
 */
interface GatewayApi {

    /**
     * Register device with GOIP server
     */
    @POST("api/device/register")
    suspend fun registerDevice(@Body deviceInfo: DeviceInfo): Response<DeviceRegistrationResponse>

    /**
     * Forward SMS message to GOIP server
     */
    @POST("api/sms/receive")
    suspend fun forwardSms(@Body smsMessage: SmsMessage): Response<SmsForwardingResponse>

    /**
     * Send device heartbeat
     */
    @POST("api/device/heartbeat")
    suspend fun sendHeartbeat(@Body heartbeatData: HeartbeatData): Response<HeartbeatResponse>
}

/**
 * Response models
 */
data class DeviceRegistrationResponse(
    val success: Boolean,
    val message: String,
    val deviceId: String? = null
)

data class SmsForwardingResponse(
    val success: Boolean,
    val message: String,
    val messageId: String? = null
)

data class HeartbeatResponse(
    val success: Boolean,
    val message: String,
    val nextHeartbeatInterval: Int? = null
)

data class HeartbeatData(
    val deviceId: String,
    val batteryLevel: Int,
    val simSlots: List<com.earnbysms.smsgateway.data.model.SimSlotInfo>,
    val deviceBrandInfo: com.earnbysms.smsgateway.data.model.DeviceBrandInfo
)