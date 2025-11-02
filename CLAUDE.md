# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Earn by SMS** is an Android SMS gateway application that monitors, forwards, and processes SMS messages for earning purposes. The app operates as a foreground service with Android 15 compliance, featuring multi-SIM support and comprehensive device information collection.

**Package**: `com.network.booster.earnbysms`
**Min SDK**: 26 (Android 8.0)
**Target SDK**: 34
**Language**: Kotlin (100%) with Jetpack Compose UI

## Development Commands

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug version to connected device
./gradlew installDebug

# Clean build directory
./gradlew clean

# Run all tests
./gradlew test

# Run instrumented tests on device/emulator
./gradlew connectedAndroidTest
```

### Development Workflow
```bash
# Build and run in debug mode
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor logs for SMS processing
adb logcat | grep -E "(SMSGateway|SMSReceiver|EarnBySMS)"

# Check foreground service status
adb shell dumpsys activity services | grep SMSGateway
```

## Architecture Overview

### Core Components

**MVVM Architecture with Repository Pattern**
- **MainActivity**: Entry point with permission handling and service initialization
- **SMSGatewayService**: Foreground service for continuous SMS monitoring (Android 15 compliant)
- **SMSReceiver**: High-priority broadcast receiver for SMS interception
- **BootReceiver**: Auto-starts services on device boot and app updates
- **SMSWorkManager**: Orchestrates background task execution with retry logic
- **Room Database**: Local persistence for SMS messages and device information
- **ApiService**: Retrofit-based HTTP client for server communication
- **DeviceUtils**: Comprehensive device information collection

### SMS Processing Pipeline

1. **SMS Reception**: SMS broadcast → High-priority receiver → PDU extraction → SMSData object creation
2. **Local Storage**: Room database with status tracking (PENDING, PROCESSING, SENT, FAILED, RETRY)
3. **Background Processing**: WorkManager queues messages with exponential backoff (2-minute intervals, max 3 retries)
4. **API Forwarding**: HTTP POST to server with device information and message payload
5. **Status Monitoring**: Real-time service health and message processing status

### Database Schema

**SMSMessage Entity** (app/src/main/java/com/network/booster/earnbysms/data/entity/SMSMessage.kt):
- UUID primary key with indexed columns for performance
- Sender phone number with international formatting support
- Message body with Unicode encoding and length validation
- SIM slot and subscription ID tracking for multi-SIM devices
- Processing status with retry count and timestamp tracking
- Metadata fields for analytics (word count, device ID, message type)

**DeviceInfo Entity** (app/src/main/java/com/network/booster/earnbysms/data/entity/DeviceInfo.kt):
- Hardware details (manufacturer, model, brand, hardware version)
- Software information (Android version, SDK level, build fingerprint)
- Telephony data (carrier info, network type, multi-SIM details)
- Battery and storage metrics for monitoring
- Root detection and security status indicators

### Network Layer

**API Endpoints** (configured in ApiService):
- `/api/devices/register` - Device registration with comprehensive profiling
- `/api/devices/heartbeat` - Periodic health monitoring (5-minute intervals)
- `/api/sms/forward` - SMS message forwarding with delivery confirmation
- `/api/devices/config` - Dynamic configuration updates from server
- `/api/devices/status` - Status reporting and error diagnostics

**Network Configuration**:
- Timeout: 30 seconds for connection/read/write operations
- Retry: Automatic connection retry with exponential backoff
- Logging: Debug-level HTTP request/response logging for development
- Base URL: Configurable via BuildConfig for different environments

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

## Foreground Service Implementation

### Android 15 Compliance
The SMSGatewayService uses `foregroundServiceType="dataSync|connectedDevice"` to comply with Android 15 requirements for continuous background processing.

### Service Lifecycle
- **Start**: Manual start from MainActivity + auto-start on boot via BootReceiver
- **Notification**: Persistent notification showing service status, uptime, and message count
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
- **AndroidX**: Latest stable versions for lifecycle, navigation, and architecture components

### Background Processing
- **WorkManager**: 2.9.0 with Hilt dependency injection integration
- **Coroutines**: 1.7.3 for asynchronous operations with proper dispatcher usage
- **Foreground Service**: Android 15 compliant with proper lifecycle management

### Database & Network
- **Room**: 2.6.1 with KTX extensions and migration support
- **Retrofit**: 2.9.0 with Gson converter and OkHttp 4.12.0
- **OkHttp Logging**: Debug-level HTTP request/response logging

### UI/UX
- **Material Design 3**: Following latest Android design guidelines
- **Dynamic Colors**: Android 12+ theming support
- **Edge-to-Edge**: Full-screen immersive experience
- **Permissions UI**: Compose-based permission handling with educational content

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
- **MainActivity**: `app/src/main/java/com/network/booster/earnbysms/MainActivity.kt`
- **SMS Gateway Service**: `app/src/main/java/com/network/booster/earnbysms/service/SMSGatewayService.kt`
- **SMS Receiver**: `app/src/main/java/com/network/booster/earnbysms/receiver/SMSReceiver.kt`
- **Boot Receiver**: `app/src/main/java/com/network/booster/earnbysms/receiver/BootReceiver.kt`

### Data Layer
- **Database Entities**: `app/src/main/java/com/network/booster/earnbysms/data/entity/`
- **DAO Interfaces**: `app/src/main/java/com/network/booster/earnbysms/data/dao/`
- **Repository**: `app/src/main/java/com/network/booster/earnbysms/data/repository/`

### Network Layer
- **API Service**: `app/src/main/java/com/network/booster/earnbysms/network/ApiService.kt`
- **Network Models**: `app/src/main/java/com/network/booster/earnbysms/network/model/`

### UI Layer
- **Compose UI**: `app/src/main/java/com/network/booster/earnbysms/ui/`
- **ViewModels**: `app/src/main/java/com/network/booster/earnbysms/ui/viewmodel/`

### Workers
- **SMS Forwarding**: `app/src/main/java/com/network/booster/earnbysms/workers/SMSForwardingWorker.kt`
- **Heartbeat**: `app/src/main/java/com/network/booster/earnbysms/workers/HeartbeatWorker.kt`
- **Device Registration**: `app/src/main/java/com/network/booster/earnbysms/workers/DeviceRegistrationWorker.kt`

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

### Permission Handling
- SMS permissions require user approval on Android 6.0+
- Phone state permissions need additional justification on Android 10+
- Foreground service permissions changed in Android 15

### Service Limitations
- Android limits foreground services to 6 hours total
- Use BootReceiver for automatic restart on boot
- Implement proper timeout handling and service restart logic

### Network Reliability
- Implement offline queuing for network failures
- Use exponential backoff for API retries
- Monitor network connectivity and adapt behavior accordingly

### Multi-SIM Complexity
- Subscription IDs can change during app lifecycle
- Handle SIM removal and insertion events gracefully
- Test on multiple devices with different carrier configurations