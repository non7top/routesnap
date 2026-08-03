#!/usr/bin/env bash
# Usage: ./scripts/check-ci.sh [PR_NUMBER]
# If PR_NUMBER is omitted, uses the PR for the current branch.
set -euo pipefail

PR="${1:-}"
if [[ -z "$PR" ]]; then
  PR=$(gh pr view --json number --jq '.number' 2>/dev/null) || {
    echo "No open PR for current branch. Pass a PR number as argument." >&2
    exit 1
  }
fi

echo "=== CI checks for PR #$PR ==="
gh pr checks "$PR"

FAILED=$(gh pr checks "$PR" --json name,bucket --jq '[.[] | select(.bucket=="fail")] | length')
if [[ "$FAILED" -gt 0 ]]; then
  echo ""
  echo "FAILED checks:"
  gh pr checks "$PR" --json name,bucket,link \
    --jq '.[] | select(.bucket=="fail") | "  \(.name): \(.link)"'
  exit 1
else
  PENDING=$(gh pr checks "$PR" --json bucket --jq '[.[] | select(.bucket=="pending")] | length')
  if [[ "$PENDING" -gt 0 ]]; then
    echo ""
    echo "$PENDING check(s) still pending."
    exit 2
  fi
  echo ""
  echo "All checks passed."
fi
