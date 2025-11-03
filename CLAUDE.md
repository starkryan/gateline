# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Earn by SMS** is an Android SMS gateway application that monitors, forwards, and processes SMS messages for earning purposes. The app operates as a foreground service with Android 15 compliance, featuring multi-SIM support and comprehensive device information collection.

**Package**: `com.earnbysms.smsgateway`
**Min SDK**: 26 (Android 8.0)
**Target SDK**: 35
**Language**: Kotlin (100%) with Jetpack Compose UI

## Development Commands

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build specific variants
./gradlew assembleInstallerDebug
./gradlew assembleMainappDebug

# Install debug version to connected device
./gradlew installDebug

# Clean build directory
./gradlew clean

# Run all tests
./gradlew test

# Run instrumented tests on device/emulator
./gradlew connectedAndroidTest

# Generate test coverage report
./gradlew jacocoTestReport
```

### Development Workflow
```bash
# Build and run in debug mode
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs for SMS processing
adb logcat | grep -E "(SMSGateway|SMSReceiver|EarnBySMS)"

# Check foreground service status
adb shell dumpsys activity services | grep SMSGateway

# Check app variants available
./gradlew tasks --group=build
```

## Architecture Overview

### Core Components

**Repository Pattern with Clean Architecture**
- **MainActivity**: Entry point with permission handling and service initialization (`com.earnbysms.smsgateway.presentation.activity.MainActivity`)
- **SMSGatewayService**: Foreground service for continuous SMS monitoring (Android 15 compliant) - 60-second heartbeat
- **SMSReceiver**: High-priority broadcast receiver for SMS interception
- **BootReceiver**: Auto-starts services on device boot and app updates
- **GatewayRepository**: Core business logic for device registration, SMS forwarding, and heartbeat management
- **GatewayApi**: Retrofit interface for HTTP communication with GOIP server
- **DeviceUtils**: Comprehensive device information collection and phone number detection

### SMS Processing Pipeline

1. **SMS Reception**: SMS broadcast → High-priority receiver → Service communication
2. **Service Processing**: SMSGatewayService receives intent → Creates SmsMessage object
3. **Network Forwarding**: Direct HTTP POST to GOIP server with device context
4. **Status Monitoring**: Real-time service health with 60-second heartbeat intervals
5. **Error Handling**: Automatic retry with consecutive failure tracking

### App Variants

The project supports two build variants:

**Installer Variant** (`installer`):
- Application ID: `com.earnbysms.smsgateway.installer`
- Limited permissions: only READ_PHONE_STATE
- Simplified functionality for installation/update scenarios

**Main App Variant** (`mainapp`):
- Application ID: `com.earnbysms.smsgateway.mainapp`
- Full permissions: SMS, Phone, Network, Boot, Foreground Service
- Complete SMS gateway functionality

### Network Layer

**API Endpoints** (configured in GatewayApi):
- `POST /api/device/register` - Device registration with comprehensive profiling
- `POST /api/sms/receive` - SMS message forwarding with delivery confirmation
- `POST /api/device/heartbeat` - Periodic health monitoring (60-second intervals)

**Network Configuration**:
- Uses Retrofit 2.9.0 with OkHttp 4.12.0
- Base URL configured in NetworkModule
- 30-second timeouts for connection/read/write operations
- Request/response logging enabled for debugging

### Data Models

**SmsMessage** (`com.earnbysms.smsgateway.data.model.SmsMessage`):
- deviceId, sender, message, timestamp, recipient
- Optional: slotIndex, subscriptionId for multi-SIM support
- Android version, manufacturer, model for device context

**DeviceInfo** (`com.earnbysms.smsgateway.data.model.DeviceInfo`):
- deviceId, phoneNumber, simSlots, batteryLevel, deviceStatus
- deviceBrandInfo with hardware details and signal status

**SimSlotInfo** (`com.earnbysms.smsgateway.data.model.SimSlotInfo`):
- slotIndex, carrierName, phoneNumber, operatorName, signalStatus

### Multi-SIM Support

The app provides comprehensive multi-SIM support:
- Automatic detection of active SIM subscriptions
- Subscription ID and SIM slot index tracking
- Multiple fallback methods for phone number detection
- Carrier-specific phone number detection (Jio, Airtel, VI)
- Signal strength monitoring per SIM slot

## Key Permissions and Security

### Critical Permissions
```xml
<!-- SMS and Phone Permissions -->
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />

<!-- Foreground Service (Android 15 Compliant) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

### Security Features
- **Device ID Collection**: Multiple fallback methods (ANDROID_ID + Settings.Secure + UUID generation)
- **Runtime Permission Handling**: Proper permission requests with educational UI
- **Network Security**: HTTPS-only communication with certificate validation
- **Data Encryption**: Room database encryption capabilities for sensitive data
- **Root Detection**: Built-in security checks to prevent execution on rooted devices

### Stealth Mode Implementation
The app implements several stealth techniques to minimize visibility:

**Notification Hiding**:
- Uses `NotificationManager.IMPORTANCE_MIN` for lowest priority
- Empty title and text content to avoid displaying information
- `VISIBILITY_SECRET` to hide from lock screen
- No sound, vibration, or lights
- System icon instead of app icon to blend in

**Channel Configuration**:
- Channel named "System Service" instead of "SMS Gateway"
- No badge display or visual indicators
- Silent operation with no user interruptions

**Service Behavior**:
- Foreground service type "dataSync|connectedDevice" for Android 15 compliance
- Minimal resource footprint and battery usage
- Background operation with no visible UI elements

## Foreground Service Implementation

### Android 15 Compliance
The SMSGatewayService uses `foregroundServiceType="dataSync|connectedDevice"` to comply with Android 15 requirements for continuous background processing.

### Service Lifecycle
- **Start**: Manual start from MainActivity + auto-start on boot via BootReceiver
- **Stealth Notification**: Hidden notification with minimal visibility (IMPORTANCE_MIN, empty title/text)
- **Timeout Handling**: Graceful restart mechanism for 6-hour Android service limits
- **Resource Management**: Proper coroutine cancellation and resource cleanup

## Testing Strategy

### Unit Tests (app/src/test/)
- Repository layer testing with mocked dependencies
- WorkManager worker testing with test harness
- SMS message parsing and validation logic
- Device information collection utilities

### Instrumented Tests (app/src/androidTest/)
- SMS receiver integration tests
- Database operations and migrations
- Service lifecycle and permission handling
- API integration with mock server

### Test Commands
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Generate test coverage report
./gradlew jacocoTestReport
```

## Technology Stack Details

### Core Framework
- **Kotlin**: 2.0.0 with strict null safety and coroutines
- **Jetpack Compose**: 2024.04.01 BOM for modern declarative UI
- **AndroidX**: Latest stable versions for lifecycle and architecture components
- **Target SDK**: 35 (Android 15) with proper foreground service compliance

### Network & Communication
- **Retrofit**: 2.9.0 with Gson converter for API communication
- **OkHttp**: 4.12.0 with logging interceptor for debugging
- **Coroutines**: 1.7.3 for asynchronous operations with proper dispatcher usage

### Dependency Injection & Architecture
- **Manual DI**: Simple, reliable manual dependency injection (no external frameworks)
- **Repository Pattern**: Clean separation of data and presentation layers
- **Result Types**: Proper error handling with Result<T> pattern

### UI/UX
- **Material Design 3**: Following latest Android design guidelines
- **Edge-to-Edge**: Full-screen immersive experience
- **Permissions UI**: Compose-based permission handling with educational content
- **Real-time Status**: Service health and message count in notifications

## Development Guidelines

### Code Standards
- **100% Kotlin**: No Java code allowed in the project
- **Coroutines**: Use proper dispatchers (IO for network/database, Main for UI)
- **Error Handling**: Comprehensive try-catch blocks with user-friendly error messages
- **Logging**: Structured logging with appropriate levels (DEBUG, INFO, WARN, ERROR)
- **Memory Management**: Proper lifecycle awareness and resource cleanup

### Performance Requirements
- **Memory Optimization**: Efficient database queries and proper coroutine usage
- **Battery Efficiency**: Minimal foreground service footprint and smart retry logic
- **Network Efficiency**: Batch operations when possible, connection pooling
- **UI Performance**: 60fps Compose animations with proper recomposition scopes

### Multi-SIM Support
- **Subscription Detection**: Automatic discovery of active SIM subscriptions
- **Message Routing**: Proper tracking of message origin per SIM slot
- **Carrier Information**: Network operator details and roaming status
- **Fallback Handling**: Graceful handling of SIM removal and network changes

## Important File Locations

### Core Application Files
- **MainActivity**: `app/src/main/java/com/earnbysms/smsgateway/presentation/activity/MainActivity.kt`
- **SMS Gateway Service**: `app/src/main/java/com/earnbysms/smsgateway/presentation/service/SMSGatewayService.kt`
- **SMS Receiver**: `app/src/main/java/com/earnbysms/smsgateway/presentation/receiver/SMSReceiver.kt`
- **Boot Receiver**: `app/src/main/java/com/earnbysms/smsgateway/presentation/receiver/BootReceiver.kt`
- **Application Class**: `app/src/main/java/com/earnbysms/smsgateway/SMSGatewayApplication.kt`

### Data Layer
- **Repository**: `app/src/main/java/com/earnbysms/smsgateway/data/repository/GatewayRepository.kt`
- **Network Models**: `app/src/main/java/com/earnbysms/smsgateway/data/model/`
- **Network Module**: `app/src/main/java/com/earnbysms/smsgateway/data/remote/NetworkModule.kt`
- **API Interface**: `app/src/main/java/com/earnbysms/smsgateway/data/remote/api/GatewayApi.kt`
- **API Provider**: `app/src/main/java/com/earnbysms/smsgateway/data/remote/api/ApiProvider.kt`

### Utilities
- **Device Utils**: `app/src/main/java/com/earnbysms/smsgateway/utils/DeviceUtils.kt`
- **Persistent Device ID**: `app/src/main/java/com/earnbysms/smsgateway/utils/PersistentDeviceId.kt`
- **SIM Slot Info**: `app/src/main/java/com/earnbysms/smsgateway/utils/SimSlotInfoCollector.kt`

### Dependency Injection
- **Manual DI**: Dependencies initialized directly in SMSGatewayService
- **API Provider**: `app/src/main/java/com/earnbysms/smsgateway/data/remote/api/ApiProvider.kt`

### UI Layer
- **Compose Theme**: `app/src/main/java/com/earnbysms/smsgateway/presentation/ui/theme/`

## Configuration Files

### Build Configuration
- **App Build**: `app/build.gradle.kts`
- **Project Build**: `build.gradle.kts`
- **Versions Catalog**: `gradle/libs.versions.toml`
- **Gradle Properties**: `gradle.properties`

### App Configuration
- **Android Manifest**: `app/src/main/AndroidManifest.xml`
- **ProGuard Rules**: `app/proguard-rules.pro`
- **Resources**: `app/src/main/res/`

## Common Issues and Solutions

### Service Communication
- SMS receiver communicates with service via intents with action "SMS_RECEIVED"
- Service manually initializes repository using simple dependency injection pattern
- Foreground service uses 60-second heartbeat with failure tracking
- **Stealth Mode**: Hidden notification using IMPORTANCE_MIN, VISIBILITY_SECRET, and empty content

### Permission Handling
- SMS permissions require user approval on Android 6.0+
- Phone state permissions need additional justification on Android 10+
- Foreground service permissions changed in Android 15 (dataSync|connectedDevice)

### Multi-SIM Phone Number Detection
- Multiple fallback methods for phone number detection per subscription
- Carrier-specific detection for Indian carriers (Jio, Airtel, VI)
- Graceful handling when phone numbers cannot be retrieved

### Network Communication
- Direct HTTP communication with GOIP server (no local database storage)
- Result-based error handling with proper logging
- 30-second timeouts with appropriate retry logic

### Signal Strength Detection
- Different API methods for different Android versions (reflection for older versions)
- Real dBm values with signal quality formatting
- Network type detection including 5G support