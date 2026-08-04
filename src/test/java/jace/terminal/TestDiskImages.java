package jace.terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates throwaway disk images for terminal tests.
 *
 * Terminal tests used to reference an absolute path in a developer's Downloads
 * folder ({@code /Users/brobert/Downloads/ProDOS_2_4_3.po}). That file is not
 * tracked, is not present on other machines, and its absence made
 * TerminalFeatureTest.testStartupWithMassStorageDisk fail unconditionally.
 * Nothing those tests assert requires a *bootable* volume -- they verify that
 * {@code -sN.dN <path>} arguments are parsed and that the named slot's card
 * actually mounts the image. A zero-filled image of the right length is enough,
 * because {@link jace.library.DiskType#determineType} keys off file length and
 * extension only.
 */
final class TestDiskImages {

    /** 280 blocks x 512 bytes -- the length DiskType maps to FLOPPY140_PO. */
    static final int PRODOS_140K_LENGTH = 143360;

    private static Path blank140k;

    private TestDiskImages() {
    }

    /**
     * Absolute path of a blank 140K ProDOS-ordered ".po" image in a temp
     * directory, created on first use and deleted when the JVM exits.
     */
    static synchronized String blankProdos140k() {
        try {
            if (blank140k == null || !Files.exists(blank140k)) {
                Path dir = Files.createTempDirectory("jace-test-disks");
                dir.toFile().deleteOnExit();
                Path image = dir.resolve("BLANK_PRODOS.po");
                Files.write(image, new byte[PRODOS_140K_LENGTH]);
                image.toFile().deleteOnExit();
                blank140k = image;
            }
            return blank140k.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test disk image", ex);
        }
    }
}
