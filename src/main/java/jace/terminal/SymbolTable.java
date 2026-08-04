package jace.terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Symbol/label table for the terminal, loaded from an assembler symbol file.
 *
 * Motivation: automated tests that name a breakpoint target ("mainloop") instead
 * of hardcoding "$4061" survive code-layout shifts. A hardcoded address silently
 * becomes the wrong address when the code above it changes size, and the test can
 * keep passing while breaking in the wrong place.
 *
 * <h2>Accepted file formats</h2>
 *
 * ACME (0.97) can emit two label formats, and this parser accepts either:
 *
 * <pre>
 *   --symbollist FILE / --labeldump :  "name  = $XXXX"   (optionally "; comment")
 *   --vicelabels FILE               :  "al C:xxxx .name" (VICE monitor format)
 * </pre>
 *
 * <h2>Provenance</h2>
 *
 * The parsing rules here were promoted from {@code jace.hardware.AcmeLabelParser}
 * in the test source tree (used by {@code Pt3PlayerRegisterTest} and
 * {@code Pt3CrashTraceTest}), rather than written afresh, so there is one set of
 * format rules. That class is presently uncommitted work belonging to another
 * effort; once it lands it should be collapsed into a thin delegate to this class.
 *
 * <h2>Resolution rules — fail loudly, never guess</h2>
 *
 * <ul>
 * <li>Bare hex still wins: "4006" is address $4006 even if a symbol is named 4006.
 *     Existing hex input behaviour is unchanged.</li>
 * <li>A leading ':' forces symbol lookup (":mainloop"), which is how you reach a
 *     symbol whose name happens to be valid hex.</li>
 * <li>Lookup is exact and case-sensitive. No prefix or fuzzy matching — a partial
 *     match resolving to the wrong address is worse than no feature at all.</li>
 * <li>An unknown name throws. A name defined at two different addresses is
 *     ambiguous and also throws, naming both addresses.</li>
 * </ul>
 */
public final class SymbolTable {

    /** VICE monitor "add label" line: {@code al C:2298 .mainloop} */
    private static final Pattern VICE_LABEL =
        Pattern.compile("^al\\s+(?:[A-Za-z]:)?([0-9a-fA-F]{1,6})\\s+\\.?(\\S+)$");

    /**
     * Shared across terminal modes and terminal sessions, matching how MonitorMode
     * persists breakpoints and watches in statics: a symbol file loaded at the main
     * prompt has to be visible to the monitor commands it forwards to.
     */
    private static final Map<String, Integer> SYMBOLS = new TreeMap<>();

    /** Names seen with conflicting addresses; resolving these must fail, not pick one. */
    private static final Set<String> AMBIGUOUS = new HashSet<>();

    private static String lastLoadedFrom = null;

    private SymbolTable() {
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Parses a symbol file without touching the shared table. */
    public static Map<String, Integer> parse(Path labelFile) throws IOException {
        return parse(Files.readAllLines(labelFile));
    }

    /** Parses symbol-file lines without touching the shared table. */
    public static Map<String, Integer> parse(List<String> lines) {
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

    /** "name = $XXXX" — ACME --symbollist / --labeldump format. */
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

    // ------------------------------------------------------------------
    // Shared table
    // ------------------------------------------------------------------

    /**
     * Loads a symbol file into the shared table, merging with anything already loaded.
     * A name redefined at a different address becomes ambiguous rather than being
     * silently overwritten.
     *
     * @return the number of symbols read from this file
     */
    public static int load(Path labelFile) throws IOException {
        Map<String, Integer> parsed = parse(labelFile);
        synchronized (SYMBOLS) {
            parsed.forEach((name, address) -> {
                Integer existing = SYMBOLS.get(name);
                if (existing != null && !existing.equals(address)) {
                    AMBIGUOUS.add(name);
                } else {
                    SYMBOLS.put(name, address);
                }
            });
            lastLoadedFrom = labelFile.toString();
        }
        return parsed.size();
    }

    /** Forgets all loaded symbols. */
    public static void clear() {
        synchronized (SYMBOLS) {
            SYMBOLS.clear();
            AMBIGUOUS.clear();
            lastLoadedFrom = null;
        }
    }

    public static int size() {
        synchronized (SYMBOLS) {
            return SYMBOLS.size();
        }
    }

    /** @return path of the most recently loaded symbol file, or null if none */
    public static String getLastLoadedFrom() {
        synchronized (SYMBOLS) {
            return lastLoadedFrom;
        }
    }

    /** Unmodifiable snapshot of the shared table, ordered by name. */
    public static Map<String, Integer> getSymbols() {
        synchronized (SYMBOLS) {
            return Collections.unmodifiableMap(new TreeMap<>(SYMBOLS));
        }
    }

    /** @return true if the token is a symbol name that can be resolved right now */
    public static boolean isKnown(String name) {
        synchronized (SYMBOLS) {
            return SYMBOLS.containsKey(name) && !AMBIGUOUS.contains(name);
        }
    }

    /**
     * Resolves a symbol name to an address.
     *
     * @param name exact, case-sensitive symbol name, with or without a leading ':'
     * @throws NumberFormatException if the name is unknown or ambiguous. Callers
     *         already treat NumberFormatException as "invalid address", so the
     *         explanatory message travels the existing error path.
     */
    public static int resolve(String name) throws NumberFormatException {
        String key = name.startsWith(":") ? name.substring(1) : name;
        synchronized (SYMBOLS) {
            if (AMBIGUOUS.contains(key)) {
                throw new NumberFormatException(
                    "ambiguous symbol '" + key + "' — defined at more than one address; "
                    + "reload with a single symbol file");
            }
            Integer address = SYMBOLS.get(key);
            if (address != null) {
                return address & 0xFFFF;
            }
            if (SYMBOLS.isEmpty()) {
                throw new NumberFormatException(
                    "unknown symbol '" + key + "' — no symbol file loaded; "
                    + "use 'symbols <file>' (acme --symbollist or --vicelabels output)");
            }
            throw new NumberFormatException(
                "unknown symbol '" + key + "' — not in " + lastLoadedFrom
                + " (" + SYMBOLS.size() + " symbols loaded); names are case-sensitive "
                + "and matched exactly");
        }
    }

    /**
     * Resolves a token that may be either hex or a symbol name.
     *
     * Hex is tried first so existing hex input is bit-for-bit unaffected; a leading
     * ':' skips the hex attempt. When a token is valid hex *and* names a symbol, hex
     * wins and the collision is reported to {@code onAmbiguity} so it cannot pass
     * unnoticed.
     *
     * @param token the address token: "4006", "$4006", "0x4006", "mainloop", ":4006"
     * @param onAmbiguity called with a warning when a hex token also names a symbol
     * @throws NumberFormatException if the token is neither valid hex nor a known symbol
     */
    public static int resolveAddressOrSymbol(String token, java.util.function.Consumer<String> onAmbiguity)
            throws NumberFormatException {
        if (token == null || token.isEmpty()) {
            throw new NumberFormatException("empty address");
        }
        if (token.startsWith(":")) {
            return resolve(token);
        }
        String hexStr = token;
        if (hexStr.startsWith("$")) {
            hexStr = hexStr.substring(1);
        } else if (hexStr.startsWith("0x") || hexStr.startsWith("0X")) {
            hexStr = hexStr.substring(2);
        }
        try {
            int address = Integer.parseInt(hexStr, 16) & 0xFFFF;
            if (onAmbiguity != null && isKnown(token)) {
                onAmbiguity.accept(String.format(
                    "Warning: '%s' is both valid hex and a symbol; using hex $%04X. "
                    + "Write ':%s' to mean the symbol ($%04X).",
                    token, address, token, resolve(token)));
            }
            return address;
        } catch (NumberFormatException notHex) {
            return resolve(token);
        }
    }
}
