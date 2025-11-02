#!/bin/bash

echo "🔄 Starting comprehensive file recovery from git blobs..."

# Get all dangling blobs
blobs=$(git fsck --lost-found | grep "dangling blob" | cut -d' ' -f3)

recovered_count=0
for blob in $blobs; do
    # Get first few lines to identify file type
    content=$(git show $blob | head -10)

    # Skip if content looks like binary or corrupted
    if echo "$content" | grep -q $'\0'; then
        continue
    fi

    # Try to identify file type and save accordingly
    if echo "$content" | grep -q "package com.earnbysms.smsgateway"; then
        # It's a Kotlin file
        if echo "$content" | grep -q "class MainActivity"; then
            git show $blob > app/src/main/java/com/earnbysms/smsgateway/presentation/activity/MainActivity.kt
            echo "✅ Recovered MainActivity.kt"
            ((recovered_count++))
        elif echo "$content" | grep -q "class SMSGatewayService"; then
            git show $blob > app/src/main/java/com/earnbysms/smsgateway/presentation/service/SMSGatewayService.kt
            echo "✅ Recovered SMSGatewayService.kt"
            ((recovered_count++))
        elif echo "$content" | grep -q "class SMSReceiver"; then
            git show $blob > app/src/main/java/com/earnbysms/smsgateway/presentation/receiver/SMSReceiver.kt
            echo "✅ Recovered SMSReceiver.kt"
            ((recovered_count++))
        elif echo "$content" | grep -q "class BootReceiver"; then
            git show $blob > app/src/main/java/com/earnbysms/smsgateway/presentation/receiver/BootReceiver.kt
            echo "✅ Recovered BootReceiver.kt"
            ((recovered_count++))
        elif echo "$content" | grep -q "class SMSGatewayApplication"; then
            git show $blob > app/src/main/java/com/earnbysms/smsgateway/SMSGatewayApplication.kt
            echo "✅ Recovered SMSGatewayApplication.kt"
            ((recovered_count++))
        elif echo "$content" | grep -q "class GatewayRepository"; then
            git show $blob > app/src/main/java/com/earnbysms/smsgateway/data/repository/GatewayRepository.kt
            echo "✅ Recovered GatewayRepository.kt"
            ((recovered_count++))
        elif echo "$content" | grep -q "data class "; then
            git show $blob > "temp_model_${blob}.kt"
            echo "✅ Recovered model file: temp_model_${blob}.kt"
            ((recovered_count++))
        else
            # Save as unknown Kotlin file
            git show $blob > "temp_unknown_${blob}.kt"
            echo "✅ Recovered unknown Kotlin file: temp_unknown_${blob}.kt"
            ((recovered_count++))
        fi
    elif echo "$content" | grep -q "<?xml"; then
        # It's an XML file
        git show $blob > "temp_xml_${blob}.xml"
        echo "✅ Recovered XML file: temp_xml_${blob}.xml"
        ((recovered_count++))
    elif echo "$content" | grep -q "plugins"; then
        # Likely build.gradle.kts
        git show $blob > build.gradle.kts
        echo "✅ Recovered build.gradle.kts"
        ((recovered_count++))
    elif echo "$content" | grep -q "distributionBase\|distributionPath"; then
        # gradle.properties
        git show $blob > gradle.properties
        echo "✅ Recovered gradle.properties"
        ((recovered_count++))
    elif echo "$content" | grep -q "android {"; then
        # Likely app build.gradle.kts
        git show $blob > app/build.gradle.kts
        echo "✅ Recovered app/build.gradle.kts"
        ((recovered_count++))
    elif echo "$content" | grep -q "include\|rootProject"; then
        # settings.gradle.kts
        git show $blob > settings.gradle.kts
        echo "✅ Recovered settings.gradle.kts"
        ((recovered_count++))
    elif echo "$content" | grep -q "[versions]"; then
        # libs.versions.toml
        git show $blob > gradle/libs.versions.toml
        echo "✅ Recovered gradle/libs.versions.toml"
        ((recovered_count++))
    else
        # Save as unknown text file
        git show $blob > "temp_text_${blob}.txt"
        echo "✅ Recovered text file: temp_text_${blob}.txt"
        ((recovered_count++))
    fi
done

echo "🎉 Recovery complete! Recovered $recovered_count files"