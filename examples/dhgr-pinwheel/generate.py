#!/usr/bin/env python3
"""
generate.py -- regenerate the DHGR pinwheel-over-sunset image into dhgr-pinwheel.asm.

This is the source of truth for the two 7680-byte DHGR data tables (main_data /
aux_data). The 6502 code section and the explanatory comment header in
dhgr-pinwheel.asm are left untouched; only the data tables are regenerated from
a 140x192 grid of 16-color palette indices.

Pipeline
--------
1. Build a 192-row x 140-column grid of palette indices:
   - 6-band vertical sunset: dark blue / purple / red / orange / yellow / pink
   - a 16-row checkerboard dither between each pair of bands
   - a round 7-blade pinwheel (white hub) drawn ON TOP, in display space, so it
     is circular on the 2:1 tall-pixel DHGR display.
2. Pack each row into 40 main bytes + 40 aux bytes. Each column-pair (28 screen
   pixels = 7 nibbles) is packed LSB-first into a 28-bit word, then split into
   4 bytes (aux[x], main[x], aux[x+1], main[x+1]). See pack_pair().
3. Self-check the packer against the verified solid-color byte table in
   docs/jace/advanced-assembly.md section 3d, AND on a non-uniform row via a
   pack->unpack round trip. A solid-color self-test alone CANNOT catch a
   nibble-direction or byte-order bug (all four bytes are identical for a solid
   color), so the round trip is the real guard.
4. Rewrite dhgr-pinwheel.asm: keep everything before the `main_data:` line, then
   append the freshly packed tables in the same layout.

Usage
-----
    python3 generate.py                    # regenerate dhgr-pinwheel.asm in place
    python3 generate.py --check            # run packer self-checks only, then stop
    python3 generate.py --preview out.png  # also write a no-NTSC reference image
    python3 generate.py --assemble         # also run acme -> dhgr-pinwheel.bin

Dependencies: numpy (grid) and Pillow (--preview only). pack/assemble/self-check
are stdlib-only except numpy for the grid.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASM = os.path.join(HERE, "dhgr-pinwheel.asm")
BIN = os.path.join(HERE, "dhgr-pinwheel.bin")

# --- 16-color palette (index -> RGB), from jace/core/Palette.java -------------
# NOTE: The authentic on-screen colors are the NTSC YIQ->RGB decode in
# VideoNTSC.YIQ_VALUES (Apple // colors are defined in YIQ / the NTSC color
# clock, not as picked RGB). These Palette.java values are a close-but-
# not-identical flat approximation, used for the --preview reference image.
PAL = [
    (0, 0, 0),        # 0  black
    (208, 0, 48),     # 1  red
    (0, 0, 128),      # 2  dark blue
    (255, 0, 255),    # 3  magenta
    (0, 128, 0),      # 4  green (dark)
    (128, 128, 128),  # 5  gray
    (0, 0, 255),      # 6  blue
    (96, 160, 255),   # 7  light blue
    (128, 80, 0),     # 8  brown
    (255, 128, 0),    # 9  orange
    (192, 192, 192),  # 10 light gray
    (255, 144, 128),  # 11 pink / salmon
    (0, 255, 0),      # 12 green (bright)
    (255, 255, 0),    # 13 yellow
    (64, 255, 144),   # 14 mint / spring green
    (255, 255, 255),  # 15 white
]

# --- DHGR color remap: nibble -> palette index (VideoDHGR.java) ---------------
FLIP_NYBBLE = [0, 2, 4, 6, 8, 10, 12, 14, 1, 3, 5, 7, 9, 11, 13, 15]
INV_FLIP = {v: k for k, v in enumerate(FLIP_NYBBLE)}  # palette index -> nibble

# --- design constants ----------------------------------------------------------
COLS, ROWS = 140, 192          # 140 columns (4 px each) x 192 rows
CX, CY = 70, 96                # pinwheel center in cell units (screen center)
R = 150                        # pinwheel radius in DISPLAY pixels
TWIST = 0.006                  # spiral twist factor (radians per display pixel)
SUNSET = [2, 3, 1, 9, 13, 11]  # dark blue, purple, red, orange, yellow, pink
PINWHEEL = [13, 12, 14, 6, 3, 1, 9]  # 7 blades: yellow, green, mint, blue, magenta, red, orange
HUB = 15                        # white hub
DITH = 16                       # rows of checkerboard dither between bands
BAND_ROWS = [19, 19, 18, 19, 18, 19]  # 6 bands; sum 112; + 5*16 dither = 192


def pack_pair(cells):
    """Pack 7 palette indices (one per 4-px column) into 4 DHGR bytes.

    Returns (aux0, main0, aux1, main1) for column-pair x (offsets x and x+1).
    The 7 cells go into the 28-bit word LSB-first: cell 0 -> bits 0-3, cell 1 ->
    bits 4-7, ..., cell 6 -> bits 24-27. Then b1=bits 0-6 -> aux[x],
    b2=bits 7-13 -> main[x], b3=bits 14-20 -> aux[x+1], b4=bits 21-27 -> main[x+1].
    """
    word = 0
    for i in range(7):
        word |= INV_FLIP[cells[i]] << (4 * i)
    return word & 0x7F, (word >> 7) & 0x7F, (word >> 14) & 0x7F, (word >> 21) & 0x7F


def unpack_pair(a0, m0, a1, m1):
    """Inverse of pack_pair: reconstruct the 7 palette indices from 4 bytes."""
    word = a0 | (m0 << 7) | (a1 << 14) | (m1 << 21)
    return [FLIP_NYBBLE[(word >> (4 * i)) & 0xF] for i in range(7)]


# Verified solid-color byte table from docs/jace/advanced-assembly.md section 3d.
# idx -> (aux0, main0, aux1, main1)
DOC_SOLID = {
    1: (0x08, 0x11, 0x22, 0x44), 3: (0x19, 0x33, 0x66, 0x4C),
    4: (0x22, 0x44, 0x08, 0x11), 6: (0x33, 0x66, 0x4C, 0x19),
    7: (0x3B, 0x77, 0x6E, 0x5D), 9: (0x4C, 0x19, 0x33, 0x66),
    11: (0x5D, 0x3B, 0x77, 0x6E), 12: (0x66, 0x4C, 0x19, 0x33),
    13: (0x6E, 0x5D, 0x3B, 0x77), 14: (0x77, 0x6E, 0x5D, 0x3B),
}


def self_check():
    """Verify pack_pair against the doc table (solid) + a non-uniform round trip."""
    for idx, want in DOC_SOLID.items():
        got = pack_pair([idx] * 7)
        assert got == want, f"solid color {idx}: got {got} want {want}"
    # Non-uniform round trip: this catches nibble-direction / byte-order bugs that
    # a solid-color test cannot (all four bytes are identical for a solid color).
    row = [3, 7, 12, 1, 9, 14, 5, 0, 2, 6, 11, 15, 8, 4, 10, 13]
    for k in range(0, len(row), 7):
        cells = row[k:k + 7]
        if len(cells) < 7:
            break
        assert unpack_pair(*pack_pair(cells)) == cells, \
            f"round-trip failed for cells {cells}: {unpack_pair(*pack_pair(cells))}"
    print("self-check OK: doc solid-color table + non-uniform round trip")


def generate_grid():
    import numpy as np
    assert sum(BAND_ROWS) + 5 * DITH == ROWS
    grid = np.zeros((ROWS, COLS), dtype=np.int8)
    r = 0
    for i in range(6):
        for _ in range(BAND_ROWS[i]):
            grid[r, :] = SUNSET[i]; r += 1
        if i < 5:
            a, b = SUNSET[i], SUNSET[i + 1]
            for _ in range(DITH):
                grid[r, :] = np.where((np.arange(COLS) + r) % 2 == 0, a, b); r += 1
    assert r == ROWS, f"grid rows filled {r} != {ROWS}"

    # Round pinwheel overlay, computed in DISPLAY space (2:1 tall pixels):
    # column c, row d maps to display pixel (4*c, 2*d).
    xs, ys = np.arange(COLS), np.arange(ROWS)
    dx = 4.0 * (xs[None, :] - CX)
    dy = 2.0 * (ys[:, None] - CY)
    rad2 = dx * dx + dy * dy
    in_pw = rad2 <= R * R
    dist = np.sqrt(rad2)
    theta = np.arctan2(dy, dx)
    sector = np.floor(((theta + TWIST * dist + np.pi) % (2 * np.pi)) / (2 * np.pi / 7.0)).astype(int) % 7
    for s in range(7):
        grid[in_pw & (sector == s)] = PINWHEEL[s]
    grid[rad2 <= 22.0 ** 2] = HUB
    return grid


def pack_tables(grid):
    main = [[0] * 40 for _ in range(ROWS)]
    aux = [[0] * 40 for _ in range(ROWS)]
    for row in range(ROWS):
        for k in range(20):
            a0, m0, a1, m1 = pack_pair([int(grid[row, 7 * k + i]) for i in range(7)])
            aux[row][2 * k] = a0;   aux[row][2 * k + 1] = a1
            main[row][2 * k] = m0;  main[row][2 * k + 1] = m1
    return main, aux


def emit_table(name, table):
    lines = [name + ":"]
    for row in range(ROWS):
        lines.append("    !byte " + ", ".join(f"${b:02X}" for b in table[row]))
    return lines


def build_asm(asm_path, main, aux):
    lines = open(asm_path).read().splitlines()
    try:
        md = next(i for i, l in enumerate(lines) if l.strip() == "main_data:")
    except StopIteration:
        raise SystemExit(f"could not find 'main_data:' in {asm_path}")
    head = lines[:md]  # everything before main_data: (header + code + '* = $6000')
    new = head + emit_table("main_data", main) + [""] + emit_table("aux_data", aux) + [""]
    open(asm_path, "w").write("\n".join(new) + "\n")
    return len(new)


def write_preview(grid, path):
    import numpy as np
    from PIL import Image
    rgb = np.array(PAL, dtype=np.uint8)[grid]          # (192,140,3)
    raw = np.repeat(rgb, 4, axis=1)                    # (192,560,3)
    disp = np.repeat(raw, 2, axis=0)                   # (384,560,3) true aspect
    Image.fromarray(disp).save(path)
    print(f"preview written: {path} (true aspect 560x384, no NTSC)")


def assemble():
    import subprocess
    r = subprocess.run(["acme", "-f", "plain", "-o", BIN, ASM],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit(f"acme failed: {r.stderr}")
    print(f"assembled: {BIN} ({os.path.getsize(BIN)} bytes)")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    ap.add_argument("--check", action="store_true", help="run self-checks and exit")
    ap.add_argument("--preview", metavar="PNG", help="write a no-NTSC reference image")
    ap.add_argument("--assemble", action="store_true", help="run acme to build the .bin")
    args = ap.parse_args()

    self_check()
    if args.check:
        return
    grid = generate_grid()
    main, aux = pack_tables(grid)
    build_asm(ASM, main, aux)
    print(f"regenerated {ASM} ({ROWS}x{COLS} grid -> main+aux tables)")
    if args.preview:
        write_preview(grid, args.preview)
    if args.assemble:
        assemble()


if __name__ == "__main__":
    main()
