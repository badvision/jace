#!/usr/bin/env bash
# Build and run the DHGR pinwheel example in JACE.
set -euo pipefail
JACE_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

cd "$JACE_DIR"

ASM="examples/dhgr-pinwheel/dhgr-pinwheel.asm"
APPLE="$BUILD_DIR/dhgr-pinwheel.apple"
BIN="$BUILD_DIR/dhgr-pinwheel.bin"

echo "Assembling with ACME..."
acme -f apple -o "$APPLE" "$ASM"

# Strip the 4-byte Apple II binary header.
tail -c +5 "$APPLE" > "$BIN"
echo "Binary: $(wc -c < "$BIN") bytes"

RAW="$BUILD_DIR/raw.png"
SHOT="examples/dhgr-pinwheel/dhgr-pinwheel-shot.png"
echo "Running in JACE (screenshot -> $RAW, then true-aspect -> $SHOT)..."
cat > "$BUILD_DIR/cmds.txt" <<EOF
reset
loadbin $BIN 800
800G
run
screenshot $RAW --vbl
qq
EOF

mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" < "$BUILD_DIR/cmds.txt"

# Apple II DHGR displays with 2:1 tall pixels: raw 560x192 -> 560x384. JACE
# scales the raw frame 2x on BOTH axes (1120x384), so rescale the width down to
# the true 560x384 aspect (this is what makes the pinwheel look round).
sips -z 384 560 "$RAW" --out "$SHOT" >/dev/null
echo "Done. Screenshot (true aspect 560x384): $SHOT"
