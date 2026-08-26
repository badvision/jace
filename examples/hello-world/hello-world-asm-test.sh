#!/bin/bash
# hello-world-asm-test.sh -- Acceptance test: assemble examples/hello-world/hello.asm
# with ACME (apple format), strip the 4-byte header, load it at $800 in JACE, run it,
# and verify HELLO WORLD appears on the text screen.
# Exit codes: 0 = pass, 1 = fail, 2 = ACME missing, 124 = emulator hang.
set -u

JACE_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cd "$JACE_DIR"

command -v acme >/dev/null 2>&1 || { echo "FAIL: ACME assembler not found (see docs/jace/assembly-quickstart.md)"; exit 2; }

# Assemble (ACME 0.97: -f apple = 2-byte LE addr + 2-byte LE length + raw code; no -n flag).
acme -f apple -o "$TMP/hello.apple" "$JACE_DIR/examples/hello-world/hello.asm"
if [ $? -ne 0 ]; then
  echo "FAIL: ACME assembly failed"
  exit 1
fi

# Strip the 4-byte apple header; payload should be 29 bytes for the current hello.asm.
tail -c +5 "$TMP/hello.apple" > "$TMP/hello.bin"
size=$(wc -c < "$TMP/hello.bin" | tr -d '[:space:]')
if [ "$size" -ne 29 ]; then
  echo "WARN: payload is $size bytes, expected 29 (JACE load log will confirm)"
fi

# JACE terminal commands (stdin via FILE redirect, never a heredoc after a pipe).
cat > "$TMP/cmds.txt" <<EOF
reset
loadbin $TMP/hello.bin 800
800G
run 20000
expect "HELLO WORLD" 10
showtext
qq
EOF

# Non-negotiable: Maven only, always wrapped in timeout.
timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" 2>&1 < "$TMP/cmds.txt" | tee "$JACE_DIR/examples/hello-world/last-run-transcript.txt"
status=${PIPESTATUS[0]}

TRANSCRIPT="$JACE_DIR/examples/hello-world/last-run-transcript.txt"

if [ "$status" -eq 124 ]; then
  echo "FAIL: emulator hung (timeout 90s, exit 124)"
  exit 124
fi
if [ "$status" -ne 0 ]; then
  echo "FAIL: JACE JVM exited with code $status"
  exit 1
fi

pass=1
grep -q "Match found" "$TRANSCRIPT" || { echo "FAIL: expect did not find HELLO WORLD"; pass=0; }
awk '/=== Text Screen/,/=== End of Text Screen ===/' "$TRANSCRIPT" | grep -q "HELLO WORLD" || { echo "FAIL: showtext screen dump lacks HELLO WORLD"; pass=0; }

if [ "$pass" -eq 1 ]; then
  echo "PASS: HELLO WORLD confirmed on emulated text screen (expect match + showtext)"
  echo "Transcript: $TRANSCRIPT"
  exit 0
else
  echo "FAIL: see transcript: $TRANSCRIPT"
  exit 1
fi
