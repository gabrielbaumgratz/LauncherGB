#!/usr/bin/env bash
# What CI runs on every pull request (.github/workflows/check.yml). Run it before you push:
#
#   tools/check.sh           # everything (Android build and translation catalogues)
#   tools/check.sh android   # only the Google TV build (needs what build.sh needs)
#
set -euo pipefail
cd "$(dirname "$0")/.."
what="${1:-all}"
case "$what" in
  all|android) ;;
  *) echo "Usage: tools/check.sh [all|android]" >&2; exit 2 ;;
esac

./build.sh
if python3 -c "import sys" >/dev/null 2>&1; then PY=python3; elif python -c "import sys" >/dev/null 2>&1; then PY=python; else PY="py -3"; fi
$PY tools/translate.py --check

echo "All checks passed."
