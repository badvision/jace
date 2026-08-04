package jace.hardware;

import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.BeforeClass;
import org.junit.Test;

import jace.AbstractJaceTest;
import jace.core.RAMListener;

/**
 * DIAGNOSTIC ONLY — temporary crash-trace harness for the vt3 6502 PT3 player.
 * Not an assertion test: it runs the player over a corpus of songs and prints
 * where, if anywhere, control flow escapes the player's own address range or
 * the stack pointer runs away. That is the crash signature.
 *
 * <p>Songs come from {@code <vt3>/apple2/songs}; override the root with
 * {@code -Dvt3.home}. {@code -Dpt3.song=<substring>} narrows to matching names,
 * {@code -Dpt3.ticks=N} sets the per-song tick budget.
 */
public class Pt3CrashTraceTest extends AbstractJaceTest {

    private static final int PLAYER_BASE = 0x0800;
    /**
     * Derived from the {@code SONG_LOAD_ADDR} label, whose single source of
     * truth is {@code <vt3>/apple2/zp.s}. Note the zero-fill below starts here,
     * so this must stay above the end of the player image (currently $26D0).
     */
    private static int PT3_BASE;
    private static final int TICKS = Integer.getInteger("pt3.ticks", 700_000);

    private static Path playerBin;
    private static int addrTestEntry;
    private static int addrIrqHandler;
    private static int addrIrqExit;
    private static int playerEnd;
    private static Map<String, Integer> labels;
    private static List<Path> songs;

    @BeforeClass
    public static void resolve() throws Exception {
        playerBin = Pt3Fixtures.findPlayerBinary().orElse(null);
        Path labelFile = Pt3Fixtures.findLabels().orElse(null);
        Path vt3 = Pt3Fixtures.resolveVt3Root().orElse(null);
        assumeTrue("need player.bin + labels + vt3 root",
                playerBin != null && labelFile != null && vt3 != null);
        labels = AcmeLabelParser.parse(labelFile);
        addrTestEntry = labels.get("test_entry");
        addrIrqHandler = labels.get("irq_handler");
        addrIrqExit = labels.get("irq_exit");
        Integer songBase = labels.get("SONG_LOAD_ADDR");
        assumeTrue("labels lack SONG_LOAD_ADDR", songBase != null);
        PT3_BASE = songBase;
        playerEnd = PLAYER_BASE + (int) (Files.size(playerBin) - 2);

        String filter = System.getProperty("pt3.song", "");
        try (Stream<Path> s = Files.list(vt3.resolve("apple2").resolve("songs"))) {
            songs = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pt3"))
                     .filter(p -> filter.isEmpty()
                             || p.getFileName().toString().toLowerCase()
                                 .contains(filter.toLowerCase()))
                     .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                     .toList();
        }
        System.out.printf("player $%04X..$%04X  test_entry=$%04X  %d songs  %d ticks each%n",
                PLAYER_BASE, playerEnd, addrTestEntry, songs.size(), TICKS);
    }

    private int rd(int a) {
        return ram.read(a, jace.core.RAMEvent.TYPE.READ_DATA, false, false) & 0xFF;
    }

    @Test
    public void screenCorpusForControlFlowEscape() throws Exception {
        assumeTrue("no songs matched", !songs.isEmpty());
        int failures = 0;
        for (Path song : songs) {
            if (!runOne(song)) {
                failures++;
            }
        }
        System.out.printf("%n=== %d/%d songs faulted ===%n", failures, songs.size());
    }

    /** @return true if the song survived the tick budget. */
    private boolean runOne(Path song) throws Exception {
        List<RAMListener> traps = new ArrayList<>();
        try {
            cpu.suspend();
            computer.getMotherboard().suspend();
            CardMockingboard mb = null;
            if (ram.getCard(4).isPresent() && ram.getCard(4).get() instanceof CardMockingboard mb4) {
                mb4.MAX_IDLE_TICKS = Integer.MAX_VALUE;
                mb = mb4;
            }
            // Reloading player.bin also restores the BSS, since ACME emits the
            // !fill blocks into the image — so every song starts from clean state.
            byte[] player = Files.readAllBytes(playerBin);
            int loadAddr = (player[0] & 0xFF) | ((player[1] & 0xFF) << 8);
            for (int i = 2; i < player.length; i++) {
                ram.write(loadAddr + (i - 2), player[i], false, false);
            }
            for (int i = PT3_BASE; i < 0xC000; i++) {
                ram.write(i, (byte) 0, false, false);
            }
            byte[] pt3 = Files.readAllBytes(song);
            for (int i = 0; i < pt3.length; i++) {
                ram.write(PT3_BASE + i, pt3[i], false, false);
            }
            if (!Boolean.getBoolean("pt3.romirq")) {
                byte[] romPage = ram.activeRead.getMemoryPage(0xFFFE);
                romPage[0xFE] = (byte) (addrIrqHandler & 0xFF);
                romPage[0xFF] = (byte) ((addrIrqHandler >> 8) & 0xFF);
            }

            int[] irqCount = {0};
            traps.add(ram.addExecutionTrap("irq", addrIrqHandler, e -> irqCount[0]++));

            cpu.setProgramCounter(addrTestEntry);
            cpu.STACK = 0xFF;
            cpu.I = false;
            cpu.resume();
            if (mb != null) {
                mb.resume();
            }

            int[] pcRing = new int[48];
            int ringPos = 0;
            boolean dumped = false;
            int lastGoodPc = addrTestEntry;
            for (int i = 0; i < TICKS; i++) {
                int pc = cpu.getProgramCounter() & 0xFFFF;
                pcRing[ringPos] = pc;
                ringPos = (ringPos + 1) % 48;
                // With pt3.romirq the ROM dispatcher is in the loop, so ROM and
                // slot space are legitimate execution targets.
                boolean inPlayer = (pc >= PLAYER_BASE && pc <= playerEnd)
                        || (Boolean.getBoolean("pt3.romirq") && pc >= 0xC000);
                int sp = cpu.STACK & 0xFF;
                if ((!inPlayer && irqCount[0] > 0) || sp < 0x40) {
                    System.out.printf("%nFAULT %s%n", song.getFileName());
                    System.out.printf("  tick=%d PC=$%04X SP=$%02X A=$%02X X=$%02X Y=$%02X "
                            + "irqs=%d reason=%s%n",
                            i, pc, sp, cpu.A & 0xFF, cpu.X & 0xFF, cpu.Y & 0xFF, irqCount[0],
                            sp < 0x40 ? "STACK_RUNAWAY" : "PC_OUT_OF_RANGE");
                    System.out.print("  recent PCs: ");
                    for (int k = 0; k < 48; k++) {
                        int p = pcRing[(ringPos + k) % 48];
                        System.out.printf("%04X(%s) ", p, nameOf(p));
                    }
                    System.out.println();
                    System.out.printf("  last PC in player: $%04X = %s%n", lastGoodPc,
                            nameOf(lastGoodPc));
                    dumpState();
                    return false;
                }
                if (inPlayer) {
                    lastGoodPc = pc;
                }
                int dumpAt = Integer.getInteger("pt3.dumpat", -1);
                if (dumpAt >= 0 && irqCount[0] == dumpAt && !dumped) {
                    dumped = true;
                    System.out.printf("%n=== STATE AT IRQ %d (%s) ===%n", dumpAt,
                            song.getFileName());
                    dumpState();
                }
                cpu.doTick();
                if (mb != null) {
                    mb.doTick();
                }
            }
            cpu.suspend();
            if (irqCount[0] == 0) {
                System.out.printf("%nHANG %s — no IRQ in %d ticks (init never completed)%n",
                        song.getFileName(), TICKS);
                System.out.print("  recent PCs: ");
                for (int k = 0; k < 48; k++) {
                    int p = pcRing[(ringPos + k) % 48];
                    System.out.printf("%04X(%s) ", p, nameOf(p));
                }
                System.out.println();
                dumpState();
                return false;
            }
            System.out.printf("ok   %-52s irqs=%4d PLR_POS=$%02X/%02X PLR_POS2=$%02X%n",
                    song.getFileName(), irqCount[0], rd(labels.get("PLR_POS")),
                    rd(labels.get("PLR_NUM_POS_ACT")), rd(labels.get("PLR_POS2")));
            return true;
        } finally {
            traps.forEach(ram::removeListener);
        }
    }

    private String nameOf(int pc) {
        String best = "?";
        int bestAddr = -1;
        for (Map.Entry<String, Integer> en : labels.entrySet()) {
            int a = en.getValue();
            if (a <= pc && a > bestAddr) {
                bestAddr = a;
                best = en.getKey();
            }
        }
        return bestAddr < 0 ? "?" : best + "+" + (pc - bestAddr);
    }

    private void dumpState() {
        String[] names = {"PLR_FLAGS", "PLR_POS", "PLR_PAT", "PLR_ROW", "PLR_SPEED", "PLR_TICK",
            "PLR_NUM_POS", "PLR_NUM_POS_ACT", "PLR_NUM_POS_ACT2", "PLR_POS2", "PLR_PAT2",
            "PLR_ROW2", "PLR_LOOP", "PLR_LOOP2", "CMD_COUNT", "CH_DECODE_IDX",
            "IC_NOTE", "IC_SAMPLE", "IC_ORNAMENT", "IC_ENV_TYPE",
            "PAT_CH_LO", "PAT_CH_HI", "PAT_CH2_LO", "PAT_CH2_HI",
            "SKIP_REM", "SKIP_REM2", "PER_CH_PER", "PER_CH_PER2",
            "FILE_PTR_LO", "FILE_PTR_HI", "FILE2_PTR_LO", "FILE2_PTR_HI",
            "TS_SIZE1_LO", "TS_SIZE1_HI", "PAT_TBL_PTR_LO", "PAT_TBL_PTR_HI",
            "PAT_TBL2_PTR_LO", "PAT_TBL2_PTR_HI", "PERIOD_SCALE_IDX", "CMD_PARAM_PTR"};
        StringBuilder sb = new StringBuilder("  state:");
        for (String n : names) {
            Integer a = labels.get(n);
            if (a != null) {
                sb.append(String.format(" %s=$%02X", n, rd(a)));
            }
        }
        System.out.println(sb);
        Integer cb = labels.get("channel_blocks");
        if (cb != null) {
            for (int ch = 0; ch < 6; ch++) {
                StringBuilder b = new StringBuilder(String.format("  ch%d:", ch));
                for (int f = 0; f < 40; f++) {
                    b.append(String.format(" %02X", rd(cb + ch * 40 + f)));
                }
                System.out.println(b);
            }
        }
        for (String v : new String[]{"VEC_A1", "VEC_B1", "VEC_C1", "VEC_A2", "VEC_B2", "VEC_C2"}) {
            Integer a = labels.get(v);
            if (a != null) {
                System.out.printf("  %s = $%02X%02X%n", v, rd(a + 1), rd(a));
            }
        }
        StringBuilder z = new StringBuilder("  zp $04-$11:");
        for (int a = 0x04; a <= 0x11; a++) {
            z.append(String.format(" %02X", rd(a)));
        }
        System.out.println(z);
    }
}
