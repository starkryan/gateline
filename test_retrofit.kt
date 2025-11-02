#!/usr/bin/env kotlin

/**
 * Test script to debug Retrofit ClassCastException issue
 */

import java.net.URL
import java.net.HttpURLConnection
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

fun main() {
    println("🔍 Testing Retrofit-like parsing...")

    // Test JSON response from API
    val jsonResponse = """
        {
            "success": true,
            "message": "Device registered successfully",
            "deviceId": "test-device-123",
            "phoneNumber": "9334198143",
            "simSlotsCount": 1,
            "phoneNumbersStored": 1
        }
    """.trimIndent()

    try {
        val gson = Gson()

        // Test parsing as DeviceRegistrationResponse
        println("📊 Testing response parsing...")
        val response = gson.fromJson(jsonResponse, DeviceRegistrationResponse::class.java)
        println("✅ Parsed response: $response")

        // Test with generic wrapper (similar to Retrofit Response<T>)
        println("\n📦 Testing generic wrapper...")
        val genericType = object : TypeToken<retrofit2.Response<DeviceRegistrationResponse>>() {}.type
        println("Generic type: $genericType")

    } catch (e: Exception) {
        println("💥 Error: ${e.message}")
        e.printStackTrace()
    }
}

data class DeviceRegistrationResponse(
    val success: Boolean,
    val message: String,
    val deviceId: String? = null,
    val phoneNumber: String? = null,
    val simSlotsCount: Int? = null,
    val phoneNumbersStored: Int? = null
)