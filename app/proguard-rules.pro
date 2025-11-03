# ====================================================================
# ENHANCED PROGUARD RULES FOR EARN BY SMS APP
# Retrofit 2.9.0, OkHttp 4.12.0, Gson 2.10.1
# ====================================================================

# Keep debugging information for troubleshooting release builds
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# ====================================================================
# RETROFIT 2.9.0 COMPREHENSIVE PROTECTION
# ====================================================================

# Keep all Retrofit classes and interfaces
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# Preserve service interface methods with HTTP annotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep service interfaces themselves (critical for dynamic proxy creation)
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Preserve generic types for Response<T>, Call<T>, etc.
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.CallAdapter
-keep,allowobfuscation,allowshrinking interface retrofit2.Converter

# Preserve Retrofit exceptions for proper error handling
-keepnames class retrofit2.HttpException
-keepnames class retrofit2.Retrofit$*

# ====================================================================
# OKHTTP 4.12.0 COMPREHENSIVE PROTECTION
# ====================================================================

# Keep all OkHttp classes
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Preserve OkHttpClient and related components
-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.Request$Builder { *; }
-keep class okhttp3.Response$Builder { *; }

# Preserve interceptors (critical for logging and authentication)
-keep class okhttp3.Interceptor { *; }
-keep class okhttp3.logging.** { *; }
-keep class * implements okhttp3.Interceptor { *; }

# Preserve OkHttp exceptions for debugging
-keepnames class okhttp3.**
-keepclassmembers class okhttp3.** {
    <fields>;
    <methods>;
}

# ====================================================================
# GSON 2.10.1 COMPREHENSIVE PROTECTION
# ====================================================================

# Keep all Gson classes
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# Preserve Gson reflection-based serialization
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Preserve Gson type adapters
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

# Preserve generic type information for Gson and Retrofit
-keepattributes Signature
-keepattributes *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers,allowshrinking,allowobfuscation class * {
    <init>(...);
}

# Keep all generic type information for Retrofit
-keep,allowshrinking,allowobfuscation class * {
    *** <methods>;
}
-keep,allowshrinking,allowobfuscation interface * {
    *** <methods>;
}

# Preserve Retrofit's generic type handling
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontnote retrofit2.internal.Platform#defaultConverters

# ====================================================================
# EARN BY SMS APP SPECIFIC PROTECTION
# ====================================================================

# Preserve all data models (critical for Gson serialization)
-keep class com.earnbysms.smsgateway.data.model.** { *; }
-keepclassmembers class com.earnbysms.smsgateway.data.model.** { *; }

# Preserve API interfaces and response models
-keep class com.earnbysms.smsgateway.data.remote.api.** { *; }
-keepclassmembers class com.earnbysms.smsgateway.data.remote.api.** { *; }

# Preserve repository classes
-keep class com.earnbysms.smsgateway.data.repository.** { *; }
-keepclassmembers class com.earnbysms.smsgateway.data.repository.** { *; }

# Preserve service classes (foreground service, receivers)
-keep class com.earnbysms.smsgateway.presentation.service.** { *; }
-keep class com.earnbysms.smsgateway.presentation.receiver.** { *; }

# Preserve utility classes for device detection
-keep class com.earnbysms.smsgateway.utils.** { *; }
-keepclassmembers class com.earnbysms.smsgateway.utils.** { *; }

# ====================================================================
# LOGGING AND DEBUGGING PRESERVATION
# ====================================================================

# Preserve Android logging (remove for production if needed)
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Preserve OkHttp logging for network debugging
-keepclassmembers class okhttp3.logging.HttpLoggingInterceptor {
    private final java.util.logging.Logger logger;
}

# ====================================================================
# REFLECTION AND DYNAMIC PROXY PROTECTION
# ====================================================================

# Keep classes that might be accessed via reflection
-keepclassmembers class * {
    @com.google.gson.annotations.* <fields>;
    @retrofit2.http.* <methods>;
}

# Preserve enum classes (critical for network status codes)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ====================================================================
# ADDITIONAL SAFETY MEASURES
# ====================================================================

# Preserve Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Preserve Serializable implementations
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.FileInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Don't warn about missing native methods
-dontwarn java.lang.NativeMethod
-dontwarn java.lang.invoke.MethodHandles

# Keep all annotations
-keep @interface * {*;}
-keepclasseswithmembers class * {
    @* <methods>;
    @* <fields>;
}