#!/usr/bin/env bash
#
# Platform-contract smoke test.
#
# Boots a real Paper server via run-paper, feeds a few commands into the
# console, then asserts the plugin enabled and answered. This exists because
# some failures are invisible to both the compiler and MockBukkit:
#
#   * Paper plugins cannot declare commands in paper-plugin.yml. Calling
#     JavaPlugin#getCommand compiles fine against every API version and then
#     throws UnsupportedOperationException on a live server. MockBukkit
#     cannot catch it either: it only understands plugin.yml, so it would
#     load us down the Bukkit code path where getCommand legitimately works,
#     and happily pass.
#   * Whether a server accepts our declared api-version.
#   * Whether the Paper library loader resolves sqlite-jdbc.
#
# Usage:
#   scripts/smoke-test.sh              # default (1.21.x) server
#   scripts/smoke-test.sh runServer26  # the 26.x target, once that task exists
#
# Exit code is what matters: 0 = the plugin enabled and responded.

set -uo pipefail

TASK="${1:-runServer}"
LOG="$(dirname "$0")/../build/smoke-test-${TASK}.log"
mkdir -p "$(dirname "$LOG")"

GRADLEW="./gradlew"
[[ -f ./gradlew.bat && ! -x ./gradlew ]] && GRADLEW="./gradlew.bat"

echo ">> smoke test: $TASK"

# The server reads console commands on stdin; 'stop' shuts it down cleanly so
# the Gradle task exits on its own instead of needing a timeout.
# Start from the shipped defaults every run. Without this the server keeps
# whatever config a previous run wrote, and we would be testing a stale
# messages.yml instead of the out-of-box experience a customer gets. (The
# plugin deliberately never overwrites an owner's edited files, so only the
# test harness can force this.)
ROOT="$(dirname "$0")/.."
# runServer -> run/, runServer26 -> run26/ (the two servers have incompatible
# world data, so they cannot share a directory).
if [[ "$TASK" == "runServer26" ]]; then RUN_DIR="$ROOT/run26"; else RUN_DIR="$ROOT/run"; fi

RUN_DATA="$RUN_DIR/plugins/WelcomeFlow"
[[ -d "$RUN_DATA" ]] && rm -rf "$RUN_DATA"

# A first run has no eula.txt and the server exits immediately after writing
# one. Pre-accepting it keeps the smoke test single-pass on a fresh checkout.
mkdir -p "$RUN_DIR"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"

# Commands are fed with gaps so the async ones (stats hits the database and
# answers on a later tick) can finish before 'stop' tears the scheduler down.
{
  sleep 25          # server boot
  echo 'wf'
  echo 'wf definitelynotasubcommand'
  echo 'wf validate'
  echo 'wf stats'
  sleep 3
  echo 'wf reload'
  sleep 2
  echo 'wf validate'
  sleep 2
  echo 'stop'
} | "$GRADLEW" "$TASK" --console=plain >"$LOG" 2>&1

strip_ansi() { sed 's/\x1b\[[0-9;]*m//g' "$1"; }

fail() { echo "!! FAIL: $1"; echo "   log: $LOG"; exit 1; }

# --- assertions -------------------------------------------------------------

grep -q "Enabling WelcomeFlow" "$LOG" \
  || fail "plugin never reached the enable phase"

# The bug this script was written for surfaced exactly here.
if grep -q "Error occurred while enabling WelcomeFlow" "$LOG"; then
  echo "--- enable failure ---"
  grep -A12 "Error occurred while enabling WelcomeFlow" "$LOG" | strip_ansi /dev/stdin
  fail "plugin threw while enabling"
fi

grep -q "WelcomeFlow enabled in" "$LOG" \
  || fail "plugin did not finish enabling"

grep -qE 'Done \([0-9.]+s\)!' "$LOG" \
  || fail "server never finished booting"

# /wf must produce the help header, proving the command registered AND
# dispatched AND rendered MiniMessage.
strip_ansi "$LOG" | grep -q "WelcomeFlow commands" \
  || fail "/wf did not print help, command likely not registered"

# An unknown subcommand must be handled, not thrown.
strip_ansi "$LOG" | grep -q "Unknown subcommand" \
  || fail "/wf <garbage> was not handled gracefully"

# The bundled config must validate cleanly out of the box: if the shipped
# defaults produce warnings, every new customer sees them on first boot.
strip_ansi "$LOG" | grep -q "Every config file is valid" \
  || fail "/wf validate did not pass on the bundled default config"

# Reload must work at runtime and leave the config still valid.
strip_ansi "$LOG" | grep -q "Configuration reloaded" \
  || fail "/wf reload did not report success"

# Stats must render the funnel rather than erroring.
strip_ansi "$LOG" | grep -q "Retention funnel" \
  || fail "/wf stats did not render the funnel"

# The plugin's own classes must not appear in any stack trace. This is what
# caught Paper 26.x rejecting Maven Central in the library loader; the
# plugin still enabled, so every other assertion passed while a RuntimeException
# was being thrown out of WelcomeFlowLoader on every boot.
if grep -qE "^\s+at .*studios\.littlesquad\.welcomeflow" "$LOG"; then
  echo "--- WelcomeFlow code appears in a stack trace ---"
  grep -B10 -E "^\s+at .*studios\.littlesquad\.welcomeflow" "$LOG" | strip_ansi /dev/stdin | head -20
  fail "an exception was thrown from WelcomeFlow code"
fi

if grep -qE "UnsupportedOperationException|Could not pass event|Exception while executing" "$LOG"; then
  echo "--- unexpected exception ---"
  grep -nE "UnsupportedOperationException|Could not pass event|Exception while executing" "$LOG" | head
  fail "unexpected exception in server log"
fi

ENABLE_MS="$(strip_ansi "$LOG" | sed -n 's/.*WelcomeFlow enabled in \([0-9]*\)ms.*/\1/p' | tail -1)"
echo ">> PASS: enabled in ${ENABLE_MS:-?}ms, /wf registered and answering"
