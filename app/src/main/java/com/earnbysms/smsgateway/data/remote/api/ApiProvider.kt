package com.earnbysms.smsgateway.data.remote.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Simple API provider without Hilt
 */
object ApiProvider {

    private const val BASE_URL = "https://ungroaning-kathe-ceilinged.ngrok-free.dev/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val gatewayApi: GatewayApi by lazy {
        retrofit.create(GatewayApi::class.java)
    }
}