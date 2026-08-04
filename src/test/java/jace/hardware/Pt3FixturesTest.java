package jace.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link Pt3Fixtures}.
 *
 * These exist because Pt3PlayerRegisterTest previously hardcoded
 * {@code /private/tmp/pt3_stage2/TADCALMTS.PT3} — a path someone had populated
 * by hand.  When that scratch directory was cleaned, the test failed with an
 * obscure NoSuchFileException instead of a clear "fixture unavailable" skip.
 */
public class Pt3FixturesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ------------------------------------------------------------------
    // firstExisting
    // ------------------------------------------------------------------

    @Test
    public void firstExisting_returnsEmptyWhenNothingExists() {
        Optional<Path> found = Pt3Fixtures.firstExisting(List.of(
            Path.of("/no/such/path/a"),
            Path.of("/no/such/path/b")
        ));
        assertFalse("Nonexistent candidates must yield empty, not a bogus Path", found.isPresent());
    }

    @Test
    public void firstExisting_prefersEarlierCandidate() throws IOException {
        Path first = tmp.newFile("first.pt3").toPath();
        Path second = tmp.newFile("second.pt3").toPath();
        Optional<Path> found = Pt3Fixtures.firstExisting(List.of(first, second));
        assertEquals(first, found.orElseThrow());
    }

    @Test
    public void firstExisting_skipsMissingCandidatesAheadOfPresentOnes() throws IOException {
        Path present = tmp.newFile("present.pt3").toPath();
        Optional<Path> found = Pt3Fixtures.firstExisting(List.of(
            Path.of("/no/such/path"),
            present
        ));
        assertEquals(present, found.orElseThrow());
    }

    @Test
    public void firstExisting_ignoresDirectories() throws IOException {
        Path dir = tmp.newFolder("looks-like-a-file.pt3").toPath();
        Path realFile = tmp.newFile("real.pt3").toPath();
        Optional<Path> found = Pt3Fixtures.firstExisting(List.of(dir, realFile));
        assertEquals("A directory must not satisfy a regular-file lookup", realFile, found.orElseThrow());
    }

    // ------------------------------------------------------------------
    // vt3RootCandidates — priority ordering
    // ------------------------------------------------------------------

    @Test
    public void vt3RootCandidates_systemPropertyWinsOverEnvAndSibling() {
        List<Path> candidates = Pt3Fixtures.vt3RootCandidates(
            "/from/property", "/from/env", Path.of("/work/jace"));
        assertEquals(Path.of("/from/property"), candidates.get(0));
    }

    @Test
    public void vt3RootCandidates_envBeatsSibling() {
        List<Path> candidates = Pt3Fixtures.vt3RootCandidates(
            null, "/from/env", Path.of("/work/jace"));
        assertEquals(Path.of("/from/env"), candidates.get(0));
    }

    @Test
    public void vt3RootCandidates_fallsBackToSiblingOfWorkingDirectory() {
        List<Path> candidates = Pt3Fixtures.vt3RootCandidates(null, null, Path.of("/work/jace"));
        assertTrue("Sibling ../vt3 must be a candidate; got " + candidates,
                   candidates.contains(Path.of("/work/vt3")));
    }

    @Test
    public void vt3RootCandidates_ignoresBlankPropertyAndEnvValues() {
        List<Path> candidates = Pt3Fixtures.vt3RootCandidates("  ", "", Path.of("/work/jace"));
        assertEquals("Blank overrides must be dropped entirely",
                     List.of(Path.of("/work/vt3")), candidates);
    }

    // ------------------------------------------------------------------
    // pt3Candidates — checked-in resource preferred over vt3 songs dir
    // ------------------------------------------------------------------

    @Test
    public void pt3Candidates_prefersJaceCheckedInFixtureOverVt3SongsDirectory() {
        List<Path> candidates = Pt3Fixtures.pt3Candidates(
            Path.of("/work/jace"), Path.of("/work/vt3"), "TAD - calm_TS.pt3");
        assertEquals(Path.of("/work/jace/src/test/resources/pt3/TAD - calm_TS.pt3"),
                     candidates.get(0));
        assertEquals(Path.of("/work/vt3/apple2/songs/TAD - calm_TS.pt3"),
                     candidates.get(1));
    }

    @Test
    public void pt3Candidates_omitsVt3PathWhenVt3RootUnknown() {
        List<Path> candidates = Pt3Fixtures.pt3Candidates(
            Path.of("/work/jace"), null, "TAD - calm_TS.pt3");
        assertEquals(List.of(Path.of("/work/jace/src/test/resources/pt3/TAD - calm_TS.pt3")),
                     candidates);
    }

    @Test
    public void pt3Candidates_neverIncludesTheOldPrivateTmpScratchPath() {
        List<Path> candidates = Pt3Fixtures.pt3Candidates(
            Path.of("/work/jace"), Path.of("/work/vt3"), "TAD - calm_TS.pt3");
        assertTrue("No candidate may live under /tmp or /private/tmp — that is how this test broke",
                   candidates.stream().noneMatch(p -> p.toString().contains("tmp")));
    }

    // ------------------------------------------------------------------
    // Real environment: at least document what is (or is not) available
    // ------------------------------------------------------------------

    @Test
    public void realEnvironment_reportsResolvedPaths() {
        System.out.println("vt3 root candidates: " + Pt3Fixtures.vt3RootCandidates(
            System.getProperty("vt3.home"), System.getenv("VT3_HOME"), Path.of(System.getProperty("user.dir"))));
        System.out.println("Resolved vt3 root:   " + Pt3Fixtures.resolveVt3Root());
    }
}
