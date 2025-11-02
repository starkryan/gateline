#!/usr/bin/env kotlin

/**
 * Debug script to test device registration
 * Run this to debug registration issues
 */

import java.net.URL
import java.net.HttpURLConnection
import com.google.gson.Gson
import com.google.gson.JsonObject

fun main() {
    println("🔍 Testing Device Registration...")

    // Test the API endpoint
    val baseUrl = "https://ungroaning-kathe-ceilinged.ngrok-free.dev/"
    val registerUrl = "${baseUrl}api/device/register"

    println("📡 Testing API endpoint: $registerUrl")

    try {
        val url = URL(registerUrl)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true

        // Create test device info
        val deviceInfo = JsonObject().apply {
            addProperty("deviceId", "test-device-123")
            addProperty("manufacturer", "Debug")
            addProperty("model", "Test Device")
            addProperty("androidVersion", "Android 15")
            addProperty("sdkVersion", 35)
            addProperty("appVersion", "1.0-mainapp")
            addProperty("networkOperator", "Test Network")
            addProperty("simSlots", 1)
            addProperty("batteryLevel", 85)
            addProperty("timestamp", System.currentTimeMillis())
        }

        // Send request
        val outputStream = connection.outputStream
        outputStream.write(deviceInfo.toString().toByteArray())
        outputStream.close()

        val responseCode = connection.responseCode
        println("📊 Response Code: $responseCode")

        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            println("✅ Registration Response: $response")
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
            println("❌ Registration Failed: $errorResponse")
        }

        connection.disconnect()

    } catch (e: Exception) {
        println("💥 Network Error: ${e.message}")
        e.printStackTrace()
    }

    // Test network connectivity
    println("\n🌐 Testing Network Connectivity...")
    testNetworkConnectivity(baseUrl)
}

fun testNetworkConnectivity(baseUrl: String) {
    try {
        val url = URL(baseUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val responseCode = connection.responseCode
        println("📡 Base URL Response: $responseCode")

        if (responseCode == 200) {
            println("✅ Server is reachable")
        } else {
            println("⚠️ Server responded with: $responseCode")
        }

        connection.disconnect()

    } catch (e: Exception) {
        println("❌ Network connectivity test failed: ${e.message}")
    }
}