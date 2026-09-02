#!/bin/bash
# hello-mixed-test.sh -- Acceptance test: runs hello-mixed.bas in JACE terminal mode,
# verifies "HELLO WORLD!" appears on the text screen.
#
# This tests the DATA+POKE pattern: a BASIC program that embeds 6502 assembly bytes
# in DATA statements, POKEs them to memory at $300 (768), then CALLs the routine.
#
# Exit codes: 0 = pass, 1 = fail, 2 = ACME missing, 124 = emulator hang.
set -euo pipefail

JACE_DIR="$(cd "$(dirname "$0")" && pwd)"
MIXED_DIR="${JACE_DIR}/hello-world-mixed"
EVIDENCE_DIR="${1:-/tmp/agents/jace-mixed-bas-asm/iteration-1}"
mkdir -p "$EVIDENCE_DIR"
TRANSCRIPT="$EVIDENCE_DIR/transcript.txt"

cd "$JACE_DIR"

# --- Optional: Verify embedded bytes match assembled output ---
# If a reference .asm exists, assemble it and diff against the DATA values.
if [ -f "${MIXED_DIR}/hello-mixed.asm" ]; then
  echo "INFO: Verifying embedded DATA bytes against assembled hello-mixed.asm..."
  acme -f apple -o /tmp/agents/jace-mixed-bas-asm/mixed-ref.apple \
    "${MIXED_DIR}/hello-mixed.asm" 2>/dev/null || {
    echo "FAIL: ACME assembly of reference .asm failed"
    exit 1
  }
  # Strip 4-byte apple header, extract raw code bytes as decimal
  REF_BYTES=$(tail -c +5 /tmp/agents/jace-mixed-bas-asm/mixed-ref.apple | \
    xxd -p | tr -d '\n' | sed 's/\(..\)/0x\1\n/g' | while read h; do printf "%d " $((16#$h)); done)

  # Extract DATA values from hello-mixed.bas (lines starting with DATA)
  BAS_BYTES=$(grep '^ *[0-9]* *DATA ' "${MIXED_DIR}/hello-mixed.bas" | \
    sed 's/^ *[0-9]* *DATA //' | tr ',' '\n' | tr -d ' ')

  REF_DECIMALS=$(echo "$REF_BYTES" | tr ' ' '\n' | grep -v '^$')
  BAS_LIST=$(echo "$BAS_BYTES" | grep -v '^$')

  if [ "$(echo "$REF_DECIMALS" | tr '\n' ' ')" != "$(echo "$BAS_LIST" | tr '\n' ' ')" ]; then
    echo "FAIL: Embedded DATA values do not match assembled bytes"
    echo "  Expected (from .asm): $(echo $REF_DECIMALS | tr '\n' ' ')"
    echo "  Found   (in .bas):    $(echo $BAS_LIST | tr '\n' ' ')"
    exit 1
  fi
  echo "INFO: DATA bytes match assembled output — verified."
fi

# --- Run JACE in terminal mode with the BASIC program ---
echo "INFO: Running hello-mixed.bas in JACE terminal mode..."
timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" \
  -Dexec.args="--terminal" 2>&1 <<EOF | tee "$TRANSCRIPT"
reset
loadbasic ${MIXED_DIR}/hello-mixed.bas
run basic
expect "HELLO WORLD!" 15
st
qq
EOF
status=${PIPESTATUS[0]}

if [ "$status" -eq 124 ]; then
  echo "FAIL: emulator hung (timeout 90s, exit 124)"
  exit 124
fi
if [ "$status" -ne 0 ]; then
  echo "FAIL: JACE JVM exited with code $status"
  exit 1
fi

# --- Verify output ---
pass=1
grep -q "Loaded" "$TRANSCRIPT" || { echo "FAIL: loadbasic did not report success"; pass=0; }
grep -q "HELLO WORLD!" "$TRANSCRIPT" || { echo "FAIL: expect did not find HELLO WORLD!"; pass=0; }

if [ "$pass" -eq 1 ]; then
  echo "PASS: HELLO WORLD! confirmed on emulated text screen (expect match)"
  echo "Evidence: $TRANSCRIPT"
  exit 0
else
  echo "FAIL: see transcript: $TRANSCRIPT"
  exit 1
fi
