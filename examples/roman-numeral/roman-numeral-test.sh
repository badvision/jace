#!/bin/bash
# roman-numeral-test.sh -- Acceptance test for examples/roman-numeral/roman-numeral.asm.
#
# Assembles with ACME (apple format), strips the 4-byte header, then runs each
# input->Roman-numeral case in its OWN fresh JACE machine (one JVM per case, a few
# at a time). Within each case it waits for the Apple //e boot banner
# (`expect "Apple //"`) before jumping to $800, so the emulator is in its
# interruptible slot-6 disk-poll state and the `800G` start is deterministic.
# Verifies every result with `expect` and a `showtext` screen dump.
#
# Exit codes: 0 = pass, 1 = fail, 2 = ACME missing, 124 = emulator hang.
set -u

JACE_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cd "$JACE_DIR"

command -v acme >/dev/null 2>&1 || { echo "FAIL: ACME assembler not found (see docs/jace/assembly-quickstart.md)"; exit 2; }

ASM="$JACE_DIR/examples/roman-numeral/roman-numeral.asm"
APPLE="$TMP/roman-numeral.apple"
BIN="$TMP/roman-numeral.bin"

# Assemble (ACME 0.97: -f apple = 2-byte LE addr + 2-byte LE length + raw code; no -n flag).
acme -f apple -o "$APPLE" "$ASM"
if [ $? -ne 0 ]; then
  echo "FAIL: ACME assembly failed"
  exit 1
fi

# Strip the 4-byte apple header.
tail -c +5 "$APPLE" > "$BIN"
size=$(wc -c < "$BIN" | tr -d '[:space:]')
if [ "$size" -ne 379 ]; then
  echo "WARN: payload is $size bytes, expected 379 (JACE load log will confirm)"
fi

# Each case: "input|expected". Out-of-range inputs print '*'.
CASES=(
  "1994|MCMXCIV"
  "1456|MCDLVI"
  "44|XLIV"
  "8|VIII"
  "1|I"
  "3999|MMMCMXCIX"
  "0|*"
  "4000|*"
)

# One case = one fresh JACE machine. `type` (per-char) feeds the number, `run` lets
# the result print, `expect` + `showtext` verify it.
run_case() {
  local input="$1" want="$2" idx="$3"
  # `reset` runs the full Apple //e hardware boot: it walks the slots, prints the
  # "Apple //e" banner, then parks in the slot-6 disk-poll loop (ROM, ~$C000 space).
  # `800G` only lands reliably ONCE the machine has reached that interruptible
  # poll state, so `expect "Apple //"` drives the boot until the banner is visible
  # before we jump to $800. (Firing 800G mid-boot races and intermittently leaves
  # the machine in the ROM poll loop instead of our program.)
  cat > "$TMP/cmds-$idx.txt" <<EOF
reset
expect "Apple //" 20
loadbin $BIN 800
800G
type "$input\n"
run 50000
expect "$want" 10
showtext
qq
EOF
  ( cd "$JACE_DIR" && timeout 90 mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" \
      -Dexec.args="--terminal" 2>&1 < "$TMP/cmds-$idx.txt" > "$TMP/tx-$idx.txt" 2>&1 )
  local rc=$?
  local found=0 to=0
  grep -q "Match found"       "$TMP/tx-$idx.txt" && found=1
  grep -q "Timeout waiting"   "$TMP/tx-$idx.txt" && to=1
  # 0=pass 1=timeout 2=hang 3=no-match
  local st=0
  [ "$rc" -eq 124 ] && st=2
  [ "$to" -eq 1 ]   && st=1
  [ "$found" -eq 0 ]&& st=3
  printf '%s|%s|%s|%s|%s\n' "$idx" "$input" "$want" "$rc" "$st" >> "$TMP/results.txt"
}

: > "$TMP/results.txt"

# Run in batches of 3 (fresh JVM per case; bounded host load).
batch=()
idx=0
for c in "${CASES[@]}"; do
  input="${c%%|*}"
  want="${c##*|}"
  run_case "$input" "$want" "$idx" &
  batch+=($!)
  idx=$((idx+1))
  if [ "${#batch[@]}" -ge 3 ]; then
    wait "${batch[@]}"
    batch=()
  fi
done
[ "${#batch[@]}" -gt 0 ] && wait "${batch[@]}"

# Combined transcript for the record (one section per case).
for f in "$TMP"/tx-*.txt; do
  n=$(basename "$f" | sed 's/tx-//;s/\.txt//')
  input=$(awk -F'|' -v n="$n" '$1==n{print $2}' "$TMP/results.txt")
  want=$(awk  -F'|' -v n="$n" '$1==n{print $3}' "$TMP/results.txt")
  { echo; echo "########## case: input=$input expect=$want ##########"; cat "$f"; }
done > "$JACE_DIR/examples/roman-numeral/last-run-transcript.txt"

# Evaluate.
n=${#CASES[@]}
pass=0; fail=0
while IFS='|' read -r i input want rc st; do
  if [ "$st" -eq 0 ]; then
    pass=$((pass+1))
    echo "  PASS  $input -> $want"
  else
    fail=$((fail+1))
    case "$st" in
      1) echo "  FAIL  $input -> $want (timed out waiting for expected text)";;
      2) echo "  FAIL  $input -> $want (emulator hung, exit 124)";;
      3) echo "  FAIL  $input -> $want (expect did not match)";;
      *) echo "  FAIL  $input -> $want (rc=$rc)";;
    esac
  fi
done < "$TMP/results.txt"

if [ "$fail" -eq 0 ] && [ "$pass" -eq "$n" ]; then
  echo "PASS: all $n input->Roman-numeral cases confirmed (each in a fresh JACE machine)"
  echo "Transcript: $JACE_DIR/examples/roman-numeral/last-run-transcript.txt"
  exit 0
else
  echo "FAIL: $pass/$n passed, $fail failed. See transcript: $JACE_DIR/examples/roman-numeral/last-run-transcript.txt"
  exit 1
fi
