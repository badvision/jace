#!/usr/bin/env bash
# =============================================================================
# build-examples-disk.sh
#
# Rebuilds a bootable Apple II ProDOS 2.4.3 disk image containing every JACE
# example, from committed sources. The result boots to the ProDOS prompt; type
# a program name (RETURN) to run it.
#
#   bash examples/build-examples-disk.sh             # -> examples/jace-examples.po
#   bash examples/build-examples-disk.sh mydisk.po   # -> custom output path
#
# Requirements:
#   acme  : Apple II assembler (ACME 0.9x)   [brew install acme]
#   cp2   : CiderPress2 ProDOS disk editor    [brew install ciderpress2]
#
# Every source is committed to the repo, so this is fully reproducible:
#   prodos-base/ProDOS_2_4_3.po   base system disk (Apple ProDOS 2.4.3)
#   <example>/*.asm               6502 sources   -> assembled with ACME, added as BIN
#   <example>/*.bas               Applesoft      -> tokenized by cp2,      added as BAS
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_IMAGE="$SCRIPT_DIR/prodos-base/ProDOS_2_4_3.po"
OUT_DISK="${1:-$SCRIPT_DIR/jace-examples.po}"

# --- locate tools -----------------------------------------------------------
ACME="$(command -v acme || true)"
[ -n "$ACME" ] || { echo "FAIL: 'acme' not found.  Install: brew install acme" >&2; exit 2; }

CP2="$(command -v cp2 || true)"
[ -n "$CP2" ] || CP2="/opt/homebrew/Cellar/ciderpress2/1.2.0/libexec/cp2"
[ -x "$CP2" ] || { echo "FAIL: 'cp2' not found.  Install: brew install ciderpress2" >&2; exit 2; }

[ -f "$BASE_IMAGE" ] || { echo "FAIL: base image missing: $BASE_IMAGE" >&2; exit 1; }

# --- scratch ----------------------------------------------------------------
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
cp "$BASE_IMAGE" "$OUT_DISK"        # start from a fresh copy of the system disk

# Keep only the boot-critical system files; drop the optional ProDOS utilities
# to free room for the examples. PRODOS + BITSY.BOOT boot; BASIC.SYSTEM runs BAS;
# QUIT.SYSTEM lets you quit BASIC; CD.EXT drives the 80-column card.
for f in VIEW.README COPYIIPLUS.8.4 BLOCKWARDEN CAT.DOCTOR UNSHRINK FASTDSK \
         FASTDSK.CONF FASTDSK.SYSTEM MAKE.SMALL.P8 MINIBAS MR.FIXIT.Y2K README; do
  "$CP2" delete "$OUT_DISK" "$f" >/dev/null 2>&1 || true
done

# assemble <asm> <diskname>
#   ACME-compile, strip the 4-byte Apple loader header, drop the raw binary
#   into $BUILD named exactly <diskname> (so cp2 lands it at the ProDOS root).
assemble() {
  local asm="$1" name="$2"
  "$ACME" -f apple -o "$BUILD/$name.apple" "$asm"
  tail -c +5 "$BUILD/$name.apple" > "$BUILD/$name"
}

# add_bin <diskname> <aux-hex>
#   Add $BUILD/<diskname> as a BIN file, aux = the address to BRUN/execute from.
add_bin() {
  local name="$1" aux="$2"
  ( cd "$BUILD" && "$CP2" add --strip-paths "$OUT_DISK" "$name" )
  "$CP2" set-attr "$OUT_DISK" "type=BIN,aux=$aux" "$name"
}

# add_bas <diskname> <source.bas>
#   Keep only the numbered BASIC lines (strips any \rem doc headers), then let
#   cp2 tokenize and import as a BAS file (type BAS, aux 0x0801 are auto-set).
add_bas() {
  local name="$1" src="$2"
  if ! grep -E '^[0-9]' "$src" > "$BUILD/$name.BAS"; then
    echo "FAIL: no numbered BASIC lines in $src" >&2; exit 1
  fi
  ( cd "$BUILD" && "$CP2" import "$OUT_DISK" bas "$name.BAS" )
}

# --- 6502 assembly examples (BIN, aux = load address) -----------------------
assemble "$SCRIPT_DIR/hello-world/hello.asm"                  HELLO
add_bin    HELLO                                              0x0800

assemble "$SCRIPT_DIR/dhgr-color-wheel/dhgr-color-wheel.asm"  COLORWHEEL
add_bin    COLORWHEEL                                         0x0800

assemble "$SCRIPT_DIR/dhgr-pinwheel/pinwheel-fp.asm"          PINWHEEL
add_bin    PINWHEEL                                           0x4800

assemble "$SCRIPT_DIR/roman-numeral/roman-numeral.asm"        ROMAN
add_bin    ROMAN                                              0x0800

# --- Applesoft BASIC examples (BAS, auto-tokenized by cp2) ------------------
add_bas HELLOMIXED   "$SCRIPT_DIR/hello-world-mixed/hello-mixed.bas"
add_bas CATONRUG     "$SCRIPT_DIR/cat-on-rug/cat-on-rug.bas"
add_bas CATIMPROVED  "$SCRIPT_DIR/cat-on-rug/cat-improved.bas"
add_bas CATLORES     "$SCRIPT_DIR/cat-on-rug-lores/cat-lores.bas"
add_bas HOUSE        "$SCRIPT_DIR/house.bas"

# --- a short on-disk README describing what runs ----------------------------
cat > "$BUILD/EXAMPLES.TXT" <<'TXT'
JACE EXAMPLES DISK
==================
Type a name below and press RETURN to run it. Press RESET (cmd-ESC-.) to reboot.

  HELLO        "HELLO WORLD" in 6502 assembly
  HELLOMIXED   HELLO WORLD via BASIC DATA/POKE/CALL into $300
  COLORWHEEL   DHGR color wheel
  PINWHEEL     DHGR pinwheel, procedural math via the Applesoft FP ROM
  ROMAN        interactive decimal-to-roman (type a number)
  CATONRUG     hi-res cat on a rug
  CATIMPROVED  improved hi-res cat on a rug
  CATLORES     lo-res cat on a rug
  HOUSE        hi-res house
TXT
( cd "$BUILD" && "$CP2" add --strip-paths "$OUT_DISK" EXAMPLES.TXT )

# --- report -----------------------------------------------------------------
echo "Built: $OUT_DISK"
echo
"$CP2" list "$OUT_DISK"
