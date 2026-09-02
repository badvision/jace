#!/bin/bash
set -u

JACE_DIR="$(cd "$(dirname "$0")" && pwd)"
EVIDENCE_DIR="${1:-/tmp/agents/jace-cat-improved}"
mkdir -p "$EVIDENCE_DIR"
TRANSCRIPT="$EVIDENCE_DIR/transcript.txt"
SCREENSHOT="$EVIDENCE_DIR/cat-improved-final.png"

cd "$JACE_DIR"

# Run JACE with the improved cat program
timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" 2>&1 <<EOF | tee "$TRANSCRIPT"
reset
lbas $JACE_DIR/examples/cat-on-rug/cat-improved.bas
run basic
break 300
resume
run 6000000
cpu
screenshot --vbl $SCREENSHOT
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

pass=1
grep -q "Loaded" "$TRANSCRIPT" || { echo "FAIL: lbas did not report success"; pass=0; }
grep -q "Breakpoint hit at \$0300" "$TRANSCRIPT" || { echo "FAIL: breakpoint at \$0300 never fired"; pass=0; }

if [ "$pass" -eq 1 ]; then
  echo "PASS: program reached completion signal"
  echo "Evidence: $TRANSCRIPT"
  [ -f "$SCREENSHOT" ] && echo "Screenshot: $SCREENSHOT"
  exit 0
else
  echo "FAIL: see transcript: $TRANSCRIPT"
  exit 1
fi
