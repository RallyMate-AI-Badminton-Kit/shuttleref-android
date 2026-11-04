#!/bin/bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$PROJECT_ROOT/config/local/android-env/sdk}}"
export ANDROID_ADB_SERVER_PORT=5038
if [[ -z "${ANDROID_AVD_HOME:-}" && -d "$PROJECT_ROOT/config/local/android-env/avd" ]]; then
  export ANDROID_AVD_HOME="$PROJECT_ROOT/config/local/android-env/avd"
fi
if [[ ! -x "$ANDROID_HOME/emulator/emulator" ]]; then
  echo '模拟器尚未安装，请设置 ANDROID_HOME，并参考 README.md。'
  exit 1
fi
exec "$ANDROID_HOME/emulator/emulator" -avd ShuttleRef_API_35 -port 5554 -no-snapshot -no-boot-anim "$@"
