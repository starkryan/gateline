package com.earnbysms.smsgateway.di

import android.content.Context
import com.earnbysms.smsgateway.data.repository.GatewayRepository
import com.earnbysms.smsgateway.data.remote.api.ApiProvider
import com.earnbysms.smsgateway.data.remote.api.GatewayApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for SMS Gateway
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideGatewayApi(): GatewayApi = ApiProvider.gatewayApi

    @Provides
    @Singleton
    fun provideGatewayRepository(
        gatewayApi: GatewayApi,
        @ApplicationContext context: Context,
        gson: Gson
    ): GatewayRepository {
        return GatewayRepository(gatewayApi, context, gson)
    }

  
}