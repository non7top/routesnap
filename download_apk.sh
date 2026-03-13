#!/bin/bash
set -e

cd /tmp/routesnap-apk

# Get the token
TOKEN=$(gh auth token)

# Download the artifact
curl -L \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -o app-debug.zip \
  "https://api.github.com/repos/non7top/routesnap/actions/artifacts/5898785874/zip"

# Extract
unzip -o app-debug.zip

# List APKs
ls -la *.apk

echo "APK downloaded successfully!"
