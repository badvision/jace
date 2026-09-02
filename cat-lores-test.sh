#!/bin/bash
# cat-lores-test.sh -- Acceptance test: JACE runs cat-lores.bas to completion and
# captures a screenshot, using a hardware breakpoint (not PRINT) as the completion
# signal so the lo-res pixel buffer (which shares memory with text page 1) is never
# corrupted by print output.
#
# Completion signal: the program's last statement is `CALL 768` (768 decimal =
# $0300), where line 20 has already POKEd an RTS opcode ($96/96 decimal). We set a
# breakpoint at $0300 and resume; the emulator's RAM-listener-based breakpoint
# (jace.terminal.MonitorMode addBreakpoint, registered on RAMEvent.TYPE.EXECUTE)
# synchronously suspends the motherboard the moment the CPU fetches that address
# and prints "Breakpoint hit at $0300" to the terminal -- this is event-driven,
# not polled, unlike `run N #breakpoint` (which the JACE docs warn can miss a
# target that's only resident for one instruction). We deliberately do NOT use
# `runto`/`break`+`resume` alone (both return immediately without blocking); we
# pair `resume` with a generous `run <cycles>` so the poll loop's wall-clock delay
# gives the breakpoint's own listener time to fire and suspend before we inspect
# state, verified empirically (see docs/jace/applesoft.md's "Headless Graphics
# Program Pattern" section for the full trace).
#
# Exit codes: 0 = pass, 1 = fail, 124 = emulator hang.
set -u

JACE_DIR="$(cd "$(dirname "$0")" && pwd)"
EVIDENCE_DIR="${1:-/tmp/agents/jace-cat-lores/iteration-3}"
mkdir -p "$EVIDENCE_DIR"
TRANSCRIPT="$EVIDENCE_DIR/transcript.txt"
SCREENSHOT="$EVIDENCE_DIR/cat-lores-final.png"

cd "$JACE_DIR"

# Non-negotiable: Maven only, always wrapped in timeout.
timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" 2>&1 <<EOF | tee "$TRANSCRIPT"
reset
lbas $JACE_DIR/examples/cat-on-rug-lores/cat-lores.bas
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
grep -q "Breakpoint hit at \$0300" "$TRANSCRIPT" || { echo "FAIL: breakpoint at \$0300 (CALL 768 completion signal) never fired"; pass=0; }

if [ "$pass" -eq 1 ]; then
  echo "PASS: program reached completion signal (breakpoint at \$0300 / CALL 768)"
  echo "Evidence: $TRANSCRIPT"
  [ -f "$SCREENSHOT" ] && echo "Screenshot: $SCREENSHOT"
  exit 0
else
  echo "FAIL: see transcript: $TRANSCRIPT"
  exit 1
fi
