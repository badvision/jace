package jace.hardware;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates the external artifacts that {@link Pt3PlayerRegisterTest} needs:
 * the assembled 6502 player, its ACME label file, a {@code .pt3} song, and
 * the {@code vt3-cli} reference binary.
 *
 * <h3>Why this class exists</h3>
 * The original test hardcoded absolute paths, including a {@code .pt3} fixture
 * at {@code /private/tmp/pt3_stage2/TADCALMTS.PT3}.  Nothing created that file
 * as part of the build; someone had copied it into scratch space by hand.  When
 * {@code /private/tmp} was cleaned the test failed with a bare
 * {@code NoSuchFileException} that gave no clue what was actually missing.
 *
 * <p>Resolution order for the vt3 checkout (first hit wins):
 * <ol>
 *   <li>{@code -Dvt3.home=...} system property</li>
 *   <li>{@code VT3_HOME} environment variable</li>
 *   <li>{@code ../vt3} relative to the Maven working directory</li>
 * </ol>
 *
 * <p>Resolution order for the {@code .pt3} song:
 * <ol>
 *   <li>{@code src/test/resources/pt3/<name>} inside this repo — a fixture
 *       checked in alongside the test, so the test is self-contained</li>
 *   <li>{@code <vt3>/apple2/songs/<name>} — the upstream source of truth</li>
 * </ol>
 * No scratch/temp directory is ever consulted.
 *
 * <p>Every lookup returns {@link Optional} so callers can
 * {@code org.junit.Assume} their way to a clearly-explained skip rather than an
 * obscure failure.
 */
final class Pt3Fixtures {

    /** TS (TurboSound) song. 1138 bytes; matches the size hardcoded in test_entry. */
    static final String DEFAULT_PT3_NAME = "TAD - calm_TS.pt3";

    private Pt3Fixtures() {
    }

    // ---------------------------------------------------------------------
    // Generic helper
    // ---------------------------------------------------------------------

    /** First candidate that exists as a regular file, else empty. */
    static Optional<Path> firstExisting(List<Path> candidates) {
        return candidates.stream().filter(Files::isRegularFile).findFirst();
    }

    // ---------------------------------------------------------------------
    // vt3 checkout location
    // ---------------------------------------------------------------------

    /**
     * Candidate vt3 checkout roots in priority order. Blank overrides are dropped.
     *
     * @param propertyValue value of {@code -Dvt3.home}, may be null/blank
     * @param envValue      value of {@code $VT3_HOME}, may be null/blank
     * @param workingDir    Maven working directory (the jace checkout)
     */
    static List<Path> vt3RootCandidates(String propertyValue, String envValue, Path workingDir) {
        List<Path> candidates = new ArrayList<>();
        addIfPresent(candidates, propertyValue);
        addIfPresent(candidates, envValue);
        Path parent = workingDir.getParent();
        if (parent != null) {
            candidates.add(parent.resolve("vt3"));
        }
        return candidates;
    }

    private static void addIfPresent(List<Path> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(Path.of(value.trim()));
        }
    }

    /** The first candidate vt3 root that is an existing directory, else empty. */
    static Optional<Path> resolveVt3Root() {
        return vt3RootCandidates(System.getProperty("vt3.home"),
                                 System.getenv("VT3_HOME"),
                                 jaceRoot())
            .stream().filter(Files::isDirectory).findFirst();
    }

    static Path jaceRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    // ---------------------------------------------------------------------
    // Individual artifacts
    // ---------------------------------------------------------------------

    /**
     * Candidate locations for a {@code .pt3} song, checked-in copy first.
     *
     * @param vt3Root vt3 checkout, or null if it could not be located
     */
    static List<Path> pt3Candidates(Path jaceRoot, Path vt3Root, String songName) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(jaceRoot.resolve("src/test/resources/pt3").resolve(songName));
        if (vt3Root != null) {
            candidates.add(vt3Root.resolve("apple2/songs").resolve(songName));
        }
        return candidates;
    }

    static Optional<Path> findPt3(String songName) {
        return firstExisting(pt3Candidates(jaceRoot(), resolveVt3Root().orElse(null), songName));
    }

    /** {@code <vt3>/apple2/build/player.bin} — produced by {@code make -C apple2}. */
    static Optional<Path> findPlayerBinary() {
        return resolveVt3Root().map(r -> r.resolve("apple2/build/player.bin"))
            .filter(Files::isRegularFile);
    }

    /**
     * The assembled player, preferring {@code apple2/build/player_regtrace.bin}
     * over {@code apple2/build/player.bin}.
     *
     * <p>The {@code $FC}-trap routine {@code emit_regs_chip1} that produces
     * register frames only exists in the {@code -DREGTRACE=1} build
     * ({@code make -C apple2 regtrace}). Loading the plain {@code player.bin}
     * yields zero frames, which reads like a player bug rather than a missing
     * build.
     */
    static Optional<Path> findRegtracePlayerBinary() {
        return resolveVt3Root().flatMap(r -> firstExisting(List.of(
            r.resolve("apple2/build/player_regtrace.bin"),
            r.resolve("apple2/build/player.bin"))));
    }

    /**
     * The ACME label file emitted alongside the given player binary. The
     * regtrace build writes {@code labels_regtrace.txt}; mixing the two files
     * would resolve addresses that do not exist in the loaded image.
     */
    static Path labelsFor(Path playerBinary) {
        boolean regtrace = playerBinary.getFileName().toString().contains("regtrace");
        return playerBinary.resolveSibling(regtrace ? "labels_regtrace.txt" : "labels.txt");
    }

    /** {@code <vt3>/apple2/build/labels.txt} — ACME {@code --vicelabels} output. */
    static Optional<Path> findLabels() {
        return resolveVt3Root().map(r -> r.resolve("apple2/build/labels.txt"))
            .filter(Files::isRegularFile);
    }

    /** {@code <vt3>/target/release/vt3-cli} — produced by {@code cargo build --release}. */
    static Optional<Path> findVt3Cli() {
        return resolveVt3Root().map(r -> r.resolve("target/release/vt3-cli"))
            .filter(Files::isExecutable);
    }

    /**
     * Human-readable explanation for a skip, naming exactly what to run to fix it.
     */
    static String missingMessage(String artifact, String remedy) {
        return "PT3 register-comparison fixture unavailable: " + artifact
             + ". vt3 root resolved to " + resolveVt3Root().map(Path::toString).orElse("<not found>")
             + " (override with -Dvt3.home=/path/to/vt3 or VT3_HOME). To provide it: " + remedy;
    }
}
