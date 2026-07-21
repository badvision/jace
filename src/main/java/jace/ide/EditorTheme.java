package jace.ide;

import javafx.scene.paint.Color;

/**
 * Editor color themes with WCAG AA-compliant contrast ratios (≥4.5:1 for normal text).
 *
 * Syntax highlighting uses a canvas overlay on top of the TextArea with a JavaFX
 * blend mode so that colored rectangles multiply/screen against the TextArea's text
 * pixels, effectively producing colored text rather than background boxes.
 *
 * DARK  — canvas blend MULTIPLY, neutral fill WHITE.
 *   MULTIPLY(rect_color, white_text) ≈ rect_color   → colored text.
 *   MULTIPLY(rect_color, dark_bg)    ≈ near-black   → invisible on background.
 *
 * LIGHT — canvas blend SCREEN, neutral fill BLACK.
 *   SCREEN(rect_color, black_text)  ≈ rect_color    → colored text.
 *   SCREEN(rect_color, white_bg)    ≈ white          → invisible on background.
 *
 * Token colors are fully opaque; they represent the desired output color directly.
 */
public enum EditorTheme {

    DARK(
        /* background  */ Color.color(0.13, 0.13, 0.16),   // #202028
        /* default fg  */ Color.color(0.87, 0.87, 0.87),   // #DEDEDE  ~10.7:1 on bg
        /* keyword     */ Color.color(0.30, 0.58, 1.00),   // bright blue
        /* string      */ Color.color(0.23, 0.86, 0.23),   // bright green
        /* comment     */ Color.color(0.62, 0.66, 0.62),   // medium grey
        /* lineNumber  */ Color.color(0.95, 0.86, 0.25),   // gold
        /* label       */ Color.color(0.82, 0.45, 1.00),   // violet
        /* number      */ Color.color(1.00, 0.65, 0.25),   // orange
        /* directive   */ Color.color(0.25, 0.90, 0.90),   // cyan
        /* cursor      */ Color.color(0.87, 0.87, 0.87),
        /* selection   */ Color.color(0.20, 0.40, 0.75, 0.60)
    ),

    LIGHT(
        /* background  */ Color.color(0.97, 0.97, 0.97),   // #F7F7F7
        /* default fg  */ Color.color(0.10, 0.10, 0.10),   // #1A1A1A  ~15:1 on bg
        /* keyword     */ Color.color(0.00, 0.28, 0.90),   // dark blue
        /* string      */ Color.color(0.05, 0.48, 0.05),   // dark green
        /* comment     */ Color.color(0.35, 0.38, 0.35),   // dark grey
        /* lineNumber  */ Color.color(0.55, 0.38, 0.00),   // dark gold
        /* label       */ Color.color(0.48, 0.00, 0.72),   // dark purple
        /* number      */ Color.color(0.70, 0.28, 0.00),   // dark orange
        /* directive   */ Color.color(0.00, 0.45, 0.55),   // dark cyan
        /* cursor      */ Color.color(0.10, 0.10, 0.10),
        /* selection   */ Color.color(0.60, 0.80, 1.00, 0.60)
    );

    public final Color background;
    public final Color defaultFg;
    public final Color keyword;
    public final Color string;
    public final Color comment;
    public final Color lineNumber;
    public final Color label;
    public final Color number;
    public final Color directive;
    public final Color cursor;
    public final Color selection;

    EditorTheme(Color bg, Color dfg, Color kw, Color str, Color cmt,
                Color ln, Color lbl, Color num, Color dir, Color cur, Color sel) {
        this.background = bg;
        this.defaultFg  = dfg;
        this.keyword    = kw;
        this.string     = str;
        this.comment    = cmt;
        this.lineNumber = ln;
        this.label      = lbl;
        this.number     = num;
        this.directive  = dir;
        this.cursor     = cur;
        this.selection  = sel;
    }

    public Color colorFor(TokenStyle style) {
        return switch (style) {
            case KEYWORD     -> keyword;
            case STRING      -> string;
            case COMMENT     -> comment;
            case LINE_NUMBER -> lineNumber;
            case LABEL       -> label;
            case NUMBER      -> number;
            case DIRECTIVE   -> directive;
            default          -> defaultFg;
        };
    }
}
