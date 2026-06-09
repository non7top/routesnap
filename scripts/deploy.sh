#!/usr/bin/env bash
# Usage: ./scripts/deploy.sh [BRANCH] [ADB_SERIAL]
# Downloads the latest debug APK from CI for BRANCH and installs it via ADB.
# Defaults: current branch, first connected ADB device.
set -euo pipefail

BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
ADB_SERIAL="${2:-}"
PKG="com.routesnap.app"
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

adb_cmd() {
  if [[ -n "$ADB_SERIAL" ]]; then
    adb -s "$ADB_SERIAL" "$@"
  else
    adb "$@"
  fi
}

echo "=== Fetching latest passing CI run for branch: $BRANCH ==="
RUN_ID=$(gh run list \
  --branch "$BRANCH" \
  --json databaseId,name,status,conclusion,createdAt \
  --jq 'sort_by(.createdAt) | reverse
        | .[] | select(.name | test("CI/CD|Build"))
        | select(.conclusion == "success")
        | .databaseId' \
  | head -1)

if [[ -z "$RUN_ID" ]]; then
  echo "No passing CI/CD run found for branch '$BRANCH'." >&2
  exit 1
fi
echo "Run ID: $RUN_ID"

echo "=== Downloading APK artifact ==="
gh run download "$RUN_ID" --dir "$TMPDIR" --pattern "app-debug-*"

APK=$(find "$TMPDIR" -name "*.apk" | sort | tail -1)
if [[ -z "$APK" ]]; then
  echo "No APK found in downloaded artifacts." >&2
  exit 1
fi
echo "APK: $APK"

echo "=== Installing on device ==="
DEVICE=$(adb_cmd get-serialno 2>/dev/null || true)
echo "Device: ${ADB_SERIAL:-$DEVICE}"

# Try update first; if signatures differ, uninstall and reinstall.
if ! adb_cmd install -r "$APK" 2>&1; then
  echo "Signature mismatch — uninstalling existing package..."
  adb_cmd uninstall "$PKG" || true
  adb_cmd install "$APK"
fi

echo "=== Launching $PKG ==="
adb_cmd shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
echo "Done."
