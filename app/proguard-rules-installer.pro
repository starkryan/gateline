# Installer variant specific ProGuard rules

# Keep device ID related classes for installer
-keep class com.earnbysms.smsgateway.utils.PersistentDeviceId { *; }
-keep class com.earnbysms.smsgateway.utils.DeviceUtils { *; }

# Keep Android Settings and Build classes for device identification
-keep class android.provider.Settings$Secure { *; }
-keep class android.os.Build { *; }

# Keep installer specific activities
-keep class com.earnbysms.smsgateway.presentation.activity.InstallerMainActivity { *; }

# Remove unused SMS classes from installer
-assumenosideeffects class android.telephony.SmsManager
-assumenosideeffects class android.content.BroadcastReceiver

# Optimize for installer - smaller footprint
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify