# JACE: Running It, Disks, and Slots

Loaded on demand from `CLAUDE.md`.

## Maven vs the Native Binary

**Use the Maven/Java version for everything.** Do not use the native binary for automation.

## Native Binary (`/Users/brobert/Downloads/Jace`)

- Interactive use only (drag-and-drop disk images, manual play)
- Does NOT support `--terminal` scripting mode — parameter is silently ignored
- Throws harmless `MacAccessible` JavaFX accessibility error on startup
- **Do not use for any automated testing or CI workflows**

## Maven — The Only Way to Script Jace

All automation, testing, memory inspection, and screenshot capture goes through Maven terminal mode.

```bash
# Standard invocation — all scripting/automation
# Use slot 7 (SmartPort) for ProDOS disk images — instant reads, no spinning-disk emulation
cd ~/Documents/code/jace
mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal" <<'EOF'
bootdisk d1 /path/to/disk.po 7
run 5000000
screenshot /tmp/frame.png
mem C07F C07F
qq
EOF
```

## Slot 6 vs Slot 7 — Always Use Slot 7 for ProDOS

| Slot | Type | Load time | Use for |
|------|------|-----------|---------|
| 6 (default) | Disk ][ | Real-time spinning disk emulation — ~600s for full ProDOS boot | Floppy-specific testing only |
| 7 | SmartPort | Instant — no spin delay | **All ProDOS .po disk images** |

`bootdisk d1 /path/to/disk.po 7` — the trailing `7` selects slot 7.
`insertdisk d1 /path/to/disk.po 7` — same syntax for manual insertion.

Slot 6 (Disk ][) emulates a real floppy drive including rotation speed, making ProDOS file I/O take hundreds of real seconds. Slot 7 (SmartPort) is a virtual hard-disk interface — reads are instantaneous. All ChoplifterReverse validation should use slot 7.

**The `screenshot` command** (`ss2` alias) renders the current DHGR/HGR framebuffer directly to
a 1120×384 PNG with NTSC color — fully headless, no display window required. Use `Read` tool on
the output PNG for multimodal visual review.

## Known Issue: cadius ProDOS Disk Images (146,432 bytes)

Jace's `FloppyDisk.java` expects exactly 143,360 bytes (280 blocks × 512). Disk images built
with `cadius` are 146,432 bytes (286 blocks × 512) — the extra 3,072 bytes are zero padding.

**Fix already applied** to `src/main/java/jace/hardware/FloppyDisk.java`: truncates to 143,360
before nibblizing when the image is exactly 146,432 bytes and the trailing bytes are zero.

If this patch is ever lost, re-apply it: in `FloppyDisk.java`, before the nibblize step, add:
```java
if (diskData.length == 146432) {
    diskData = Arrays.copyOf(diskData, 143360);
}
```

## Starting Terminal Mode

```bash
# Standard — preferred
cd ~/Documents/code/jace
mvn -q exec:java -Dexec.mainClass="jace.JaceLauncher" -Dexec.args="--terminal"

# Alternative
mvn -q javafx:run -Djavafx.args="--terminal"
```

