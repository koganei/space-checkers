#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
avd="${1:-space-checkers-test}"
exec emulator -avd "$avd" -port "${2:-5554}" -no-snapshot -gpu swiftshader_indirect
