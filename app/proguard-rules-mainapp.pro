# Main App variant specific ProGuard rules

# Keep all SMS gateway components for main app
-keep class com.earnbysms.smsgateway.** { *; }

# Keep SMS and telephony related classes
-keep class android.telephony.** { *; }
-keep class android.provider.Telephony { *; }

# Keep service and receiver classes
-keep class com.earnbysms.smsgateway.presentation.service.** { *; }
-keep class com.earnbysms.smsgateway.presentation.receiver.** { *; }

# Keep network and API classes
-keep class com.earnbysms.smsgateway.network.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# Keep JSON parsing
-keep class com.google.gson.** { *; }

# Keep coroutine support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep main app specific activities
-keep class com.earnbysms.smsgateway.presentation.activity.MainAppMainActivity { *; }

# Standard Android rules
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider