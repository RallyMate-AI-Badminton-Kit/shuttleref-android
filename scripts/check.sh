#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/host
"${CC:-cc}" -std=c99 -Wall -Wextra -Itests/support/score-core/include \
  tests/support/harness/main.c tests/support/score-core/src/score_engine.c \
  tests/support/score-core/src/match_session.c tests/support/score-core/src/phone_command.c \
  -o build/host/shuttleref-match-harness
export SHUTTLEREF_MATCH_HARNESS="$PWD/build/host/shuttleref-match-harness"
# Fresh results ensure the C replay cannot be hidden by a prior skipped run.
./gradlew :core:cleanTest :core:test :app:assembleDemoDebug --no-daemon --console=plain "$@"
python3 scripts/check_test_results.py
