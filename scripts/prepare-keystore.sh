#!/bin/bash
# Script to prepare keystore for GitHub Actions
# Run this locally after creating your keystore

set -e

KEYSTORE_FILE="${1:-routesnap-release-key.jks}"

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "Error: Keystore file '$KEYSTORE_FILE' not found!"
    echo "Generate one with:"
    echo "  keytool -genkey -v -keystore $KEYSTORE_FILE -keyalg RSA -keysize 2048 -validity 10000 -alias routesnap"
    exit 1
fi

echo "=== Preparing keystore for GitHub Actions ==="
echo ""

# Encode keystore to base64
echo "Encoding keystore to base64..."
KEYSTORE_BASE64=$(base64 -w 0 "$KEYSTORE_FILE")

echo ""
echo "=== Add these secrets to your GitHub repository ==="
echo "Go to: https://github.com/non7top/routesnap/settings/secrets/actions"
echo ""
echo "1. KEYSTORE_BASE64 (paste the line below):"
echo "---"
echo "$KEYSTORE_BASE64"
echo "---"
echo ""
echo "2. KEY_ALIAS (your key alias, e.g., 'routesnap')"
echo "3. STORE_PASSWORD (your keystore password)"
echo "4. KEY_PASSWORD (your key password)"
echo ""
echo "⚠️  IMPORTANT: Never commit your actual keystore file to git!"
