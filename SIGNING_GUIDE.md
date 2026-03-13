# RouteSnap - APK Signing Guide

This guide explains how to sign RouteSnap APKs for release.

## Quick Start

### Local Development

1. **Generate a keystore** (one-time):
   ```bash
   keytool -genkey -v -keystore routesnap-release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias routesnap
   ```

2. **Create keystore.properties**:
   ```bash
   cp keystore.properties.template keystore.properties
   # Edit keystore.properties with your actual values
   ```

3. **Build signed APK**:
   ```bash
   ./gradlew assembleRelease
   ```

   Output: `app/build/outputs/apk/release/app-release.apk`

### GitHub Actions (CI/CD)

1. **Prepare keystore for GitHub**:
   ```bash
   chmod +x scripts/prepare-keystore.sh
   ./scripts/prepare-keystore.sh routesnap-release-key.jks
   ```

2. **Add secrets to GitHub**:
   - Go to: https://github.com/non7top/routesnap/settings/secrets/actions
   - Add these secrets:
     - `KEYSTORE_BASE64` - Base64 encoded keystore (from script output)
     - `KEY_ALIAS` - Your key alias (e.g., `routesnap`)
     - `STORE_PASSWORD` - Keystore password
     - `KEY_PASSWORD` - Key password

3. **Create a release**:
   ```bash
   git tag -a v1.0.0 -m "Release v1.0.0"
   git push origin v1.0.0
   ```

4. **Download signed APK** from GitHub Actions artifacts or release page.

## File Structure

```
routesnap/
├── keystore.properties.template    # Template (committed)
├── keystore.properties             # Your config (NOT committed)
├── routesnap-release-key.jks       # Your keystore (NOT committed)
├── scripts/
│   └── prepare-keystore.sh         # Helper script
└── .github/workflows/
    └── android-build.yml           # CI/CD with signing support
```

## Security Best Practices

1. **Never commit** these files:
   - `*.jks` / `*.keystore`
   - `keystore.properties`

2. **Backup your keystore** securely:
   - Store in password manager
   - Keep offline backup
   - Document recovery process

3. **Use strong passwords**:
   - Minimum 12 characters
   - Mix of letters, numbers, symbols
   - Different from other passwords

## Troubleshooting

### "Keystore was tampered with, or password was incorrect"
- Verify passwords in `keystore.properties`
- Check if keystore file is corrupted

### "SigningConfig not found"
- Ensure `keystore.properties` exists
- Verify file paths are correct

### GitHub Actions: "Keystore file not found"
- Check secrets are set correctly
- Verify `KEYSTORE_BASE64` is valid base64

## Verify APK Signature

```bash
# Using apksigner (from Android SDK)
apksigner verify --verbose app-release.apk

# Using jarsigner
jarsigner -verify -verbose -certs app-release.apk
```

## Release Checklist

- [ ] Keystore created and backed up
- [ ] `keystore.properties` configured locally
- [ ] GitHub secrets configured
- [ ] Version code incremented
- [ ] Version name updated
- [ ] Release notes written
- [ ] Signed APK tested on device
- [ ] Release published on GitHub
