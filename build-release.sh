#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Ensure environment variables
export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "=== Apparat: Building Release AAB Bundle ==="
echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"

if [ ! -f "keystore.properties" ]; then
    echo "⚠️  NOTE: 'keystore.properties' not found."
    echo "   The bundle will be signed with debug key for local inspection."
    echo "   Run './scripts/generate-keystore.sh' to generate production release keys."
    echo ""
else
    echo "✅ Found keystore.properties - Building with release signature."
fi

echo "Running ./gradlew clean :app:bundleRelease..."
./gradlew clean :app:bundleRelease

BUNDLE_PATH="app/build/outputs/bundle/release/Apparat-release.aab"
if [ ! -f "$BUNDLE_PATH" ]; then
    # Fallback search if archive name differed
    BUNDLE_PATH="$(find app/build/outputs/bundle/release -name "*.aab" | head -1)"
fi

if [ -f "$BUNDLE_PATH" ]; then
    echo ""
    echo "========================================================"
    echo " SUCCESS! Release Android App Bundle (.aab) created at:"
    echo " $BUNDLE_PATH"
    echo " Size: $(du -h "$BUNDLE_PATH" | cut -f1)"
    echo " SHA256: $(sha256sum "$BUNDLE_PATH" | cut -d' ' -f1)"
    echo "========================================================"
    echo "This is the file required by Google Play Console."
else
    echo "Error: Bundle file not found in app/build/outputs/bundle/release"
    exit 1
fi
