package com.earnbysms.smsgateway

import android.app.Application

/**
 * SMS Gateway Application class with improved initialization
 * Initializes core application components and networking
 * Uses manual dependency injection for simplicity and reliability
 */
class SMSGatewayApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize application-level components
        initializeApp()
    }

    /**
     * Initialize core application components
     */
    private fun initializeApp() {
        // Set global exception handler for better error reporting
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("SMSGatewayApp", "Uncaught exception in thread ${thread.name}", throwable)
        }

        // Initialize application components
        initializeComponents()
        
        android.util.Log.d("SMSGatewayApp", "Application initialized successfully")
    }

    /**
     * Initialize application components in proper order
     */
    private fun initializeComponents() {
        try {
            // Initialize network components
            initializeNetworkComponents()
            
            // Initialize device utilities
            initializeDeviceComponents()
            
            android.util.Log.d("SMSGatewayApp", "All components initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("SMSGatewayApp", "Failed to initialize components", e)
        }
    }

    /**
     * Initialize network components
     */
    private fun initializeNetworkComponents() {
        // Initialize API provider early to ensure network layer is ready
        try {
            com.earnbysms.smsgateway.data.remote.api.ApiProvider
            android.util.Log.d("SMSGatewayApp", "Network components initialized")
        } catch (e: Exception) {
            android.util.Log.e("SMSGatewayApp", "Failed to initialize network components", e)
        }
    }

    /**
     * Initialize device-specific components
     */
    private fun initializeDeviceComponents() {
        try {
            // Pre-warm device utilities for faster initialization
            com.earnbysms.smsgateway.utils.DeviceUtils
            android.util.Log.d("SMSGatewayApp", "Device components initialized")
        } catch (e: Exception) {
            android.util.Log.e("SMSGatewayApp", "Failed to initialize device components", e)
        }
    }
}