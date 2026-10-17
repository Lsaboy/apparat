#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

# Locate Java & keytool
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    KEYTOOL="$JAVA_HOME/bin/keytool"
elif [ -x "$HOME/tools/jdk17/bin/keytool" ]; then
    KEYTOOL="$HOME/tools/jdk17/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then
    KEYTOOL="keytool"
else
    echo "ERROR: 'keytool' not found. Please ensure Java JDK is installed."
    exit 1
fi

KEYSTORE_FILE="release.keystore"
KEY_ALIAS="apparat"

echo "====================================================="
echo " Apparat - Release Keystore Generator              "
echo "====================================================="
echo "This script will generate a production upload keystore"
echo "for Google Play Store and configure keystore.properties."
echo ""

if [ -f "$KEYSTORE_FILE" ]; then
    echo "WARNING: '$KEYSTORE_FILE' already exists!"
    read -rp "Overwrite existing keystore? (y/N): " CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
fi

# Prompt for passwords
read -rsp "Enter password for keystore and key: " PASSWORD
echo ""
read -rsp "Confirm password: " PASSWORD_CONFIRM
echo ""

if [ "$PASSWORD" != "$PASSWORD_CONFIRM" ]; then
    echo "ERROR: Passwords do not match!"
    exit 1
fi

if [ -z "$PASSWORD" ]; then
    echo "ERROR: Password cannot be empty!"
    exit 1
fi

read -rp "Enter Developer / Organization Name [Vedant]: " DNAME_CN
DNAME_CN="${DNAME_CN:-Vedant}"

echo ""
echo "Generating keystore with keytool..."
"$KEYTOOL" -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=$DNAME_CN, OU=Mobile, O=Personal, L=City, ST=State, C=IN"

# Write keystore.properties
cat <<EOF > keystore.properties
# Generated on $(date -u +"%Y-%m-%d %H:%M:%SZ")
storeFile=$KEYSTORE_FILE
storePassword=$PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$PASSWORD
EOF

chmod 600 keystore.properties
chmod 600 "$KEYSTORE_FILE"

echo ""
echo "====================================================="
echo " SUCCESS! Keystore created: $KEYSTORE_FILE"
echo " Configured: keystore.properties (git-ignored)"
echo "====================================================="
echo ""
echo "For GitHub Actions automation, save these Secrets in GitHub Repository Settings:"
echo "1. PLAY_KEYSTORE_BASE64:"
echo "   Run: base64 -w 0 $KEYSTORE_FILE"
echo "2. PLAY_KEYSTORE_PASSWORD: [the password you just entered]"
echo "3. PLAY_KEY_ALIAS: $KEY_ALIAS"
echo "4. PLAY_KEY_PASSWORD: [the password you just entered]"
echo ""
echo "Keep this keystore safe! If you lose it, you will have to request a key reset from Google Play Support."
