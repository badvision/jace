package jace.hardware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses symbol tables emitted by the ACME assembler.
 *
 * ACME can emit two different label-file formats and vt3's apple2/Makefile
 * chooses one of them:
 *
 *   --labeldump / --symbollist :  "name  = $XXXX"      (plain format)
 *   --vicelabels               :  "al C:xxxx .name"    (VICE monitor format)
 *
 * vt3's Makefile uses {@code --vicelabels} because the VICE format is what
 * {@code apple2/test_regs.sh} and the VICE/AppleWin monitors consume.  Rather
 * than change the Makefile (whose output feeds those other consumers), this
 * parser accepts either format.
 *
 * Only used by tests, hence its home in the test source tree.
 */
final class AcmeLabelParser {

    /** VICE monitor "add label" line: {@code al C:2298 .test_entry} */
    private static final Pattern VICE_LABEL =
        Pattern.compile("^al\\s+(?:[A-Za-z]:)?([0-9a-fA-F]{1,6})\\s+\\.?(\\S+)$");

    private AcmeLabelParser() {
    }

    static Map<String, Integer> parse(Path labelFile) throws IOException {
        return parse(Files.readAllLines(labelFile));
    }

    static Map<String, Integer> parse(List<String> lines) {
        Map<String, Integer> labels = new HashMap<>();
        for (String line : lines) {
            String stripped = line.replaceAll(";.*$", "").trim();
            if (stripped.isEmpty()) {
                continue;
            }
            if (!parseVice(stripped, labels)) {
                parsePlain(stripped, labels);
            }
        }
        return labels;
    }

    /**
     * "al C:xxxx .name" — ACME --vicelabels format.
     *
     * @return true if the line was recognized as a VICE label line
     */
    private static boolean parseVice(String stripped, Map<String, Integer> out) {
        Matcher m = VICE_LABEL.matcher(stripped);
        if (!m.matches()) {
            return false;
        }
        out.put(m.group(2), Integer.parseInt(m.group(1), 16));
        return true;
    }

    /** "name = $XXXX" — ACME --labeldump / --symbollist format. */
    private static void parsePlain(String stripped, Map<String, Integer> out) {
        if (!stripped.contains("=")) {
            return;
        }
        String[] parts = stripped.split("=", 2);
        if (parts.length < 2) {
            return;
        }
        String name = parts[0].trim();
        String hexStr = parts[1].trim().replaceFirst("^\\$", "");
        hexStr = hexStr.split("[^0-9a-fA-F]")[0];
        if (name.isEmpty() || hexStr.isEmpty()) {
            return;
        }
        try {
            out.put(name, Integer.parseInt(hexStr, 16));
        } catch (NumberFormatException ignored) {
            // not a label line
        }
    }
}
