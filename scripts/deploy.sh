#!/usr/bin/env bash
# Usage: ./scripts/deploy.sh [PR_OR_BRANCH] [ADB_SERIAL]
#
# Downloads the latest passing debug APK from CI and installs it via ADB.
#
# Examples:
#   ./scripts/deploy.sh                        # current branch, first device
#   ./scripts/deploy.sh 121                    # PR #121, first device
#   ./scripts/deploy.sh 121 192.168.2.229:33661
#   ./scripts/deploy.sh feature/my-branch 192.168.2.229:33661
set -euo pipefail

INPUT="${1:-}"
ADB_SERIAL="${2:-}"
PKG="com.routesnap.app"

# Resolve branch from PR number or use current branch
if [[ "$INPUT" =~ ^[0-9]+$ ]]; then
  BRANCH=$(gh pr view "$INPUT" --json headRefName --jq '.headRefName')
  echo "PR #$INPUT → branch: $BRANCH"
elif [[ -n "$INPUT" ]]; then
  BRANCH="$INPUT"
else
  BRANCH=$(git rev-parse --abbrev-ref HEAD)
fi
echo "Branch: $BRANCH"

# Keep downloads in a stable location so reruns skip the download
DLDIR="$HOME/.cache/routesnap-apk"
mkdir -p "$DLDIR"

adb_cmd() {
  if [[ -n "$ADB_SERIAL" ]]; then
    adb -s "$ADB_SERIAL" "$@"
  else
    adb "$@"
  fi
}

echo "=== Fetching latest passing CI run ==="
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

ARTIFACT_DIR="$DLDIR/app-debug-$RUN_ID"
if [[ -f "$ARTIFACT_DIR/app-debug.apk" ]]; then
  echo "=== Using cached APK ==="
else
  echo "=== Downloading APK artifact ==="
  gh run download "$RUN_ID" --dir "$DLDIR" --pattern "app-debug-$RUN_ID"
fi

APK="$ARTIFACT_DIR/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK" >&2
  exit 1
fi
echo "APK: $APK"

echo "=== Installing on device ==="
if [[ -n "$ADB_SERIAL" ]]; then
  echo "Device: $ADB_SERIAL"
else
  echo "Device: $(adb get-serialno 2>/dev/null || echo '(default)')"
fi

echo "Uninstalling existing package (if present)..."
adb_cmd uninstall "$PKG" 2>/dev/null || true

echo "Installing $APK ..."
adb_cmd install "$APK"

echo "=== Launching $PKG ==="
adb_cmd shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
echo "Done."
