package jace.ide;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Native JavaFX editor control built on RichTextFX CodeArea.
 *
 * Syntax highlighting is done via CSS style classes applied to character ranges —
 * no Canvas overlay, no blend mode tricks.  Each TokenStyle maps to a CSS class
 * (e.g. "token-keyword") and the theme writes corresponding -fx-fill rules into a
 * dynamic stylesheet injected into the scene.
 *
 * MarkerRenderer data model (addMarker/clearMarkers) is retained.  Error/warning
 * lines are surfaced as paragraph-level CSS classes ("marker-error", "marker-warning")
 * so the line background tints come from CSS without any custom painting.
 */
public class NativeEditorControl extends VBox implements EditorControl {

    private static final String FONT_FAMILY = "Monospace";
    private static final int    FONT_SIZE   = 13;

    private final CodeArea codeArea = new CodeArea();
    private final SimpleBooleanProperty dirty = new SimpleBooleanProperty(false);
    private final MarkerRenderer markerRenderer = new MarkerRenderer();
    private final ReadOnlyStringWrapper textWrapper = new ReadOnlyStringWrapper();

    private SyntaxDefinition syntaxDef = new PlainTextSyntax();
    private EditorTheme theme = EditorTheme.DARK;

    private boolean loadingText = false;

    // Find/replace
    private HBox findReplaceBar;
    private TextField findField;
    private TextField replaceField;

    // ── Constructor ──────────────────────────────────────────────────────────

    public NativeEditorControl() {
        codeArea.setStyle(
            "-fx-font-family: '" + FONT_FAMILY + "';"
            + "-fx-font-size: " + FONT_SIZE + "px;");
        codeArea.setWrapText(false);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        VBox.setVgrow(codeArea, Priority.ALWAYS);

        getChildren().add(codeArea);
        getStyleClass().add("native-editor-control");

        // Keep the ReadOnlyStringProperty wrapper in sync
        textWrapper.bind(codeArea.textProperty());

        // Dirty tracking + debounced syntax highlighting
        PauseTransition debounce = new PauseTransition(Duration.millis(150));
        debounce.setOnFinished(e -> rehighlight());
        codeArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!loadingText) dirty.set(true);
            debounce.playFromStart();
        });

        applyTheme();
    }

    // ── EditorControl ────────────────────────────────────────────────────────

    @Override public String getText() { return codeArea.getText(); }

    @Override
    public void setText(String text) {
        loadingText = true;
        try {
            codeArea.replaceText(text != null ? text : "");
            rehighlight();
        } finally {
            loadingText = false;
        }
        dirty.set(false);
    }

    @Override
    public void setSyntaxDefinition(SyntaxDefinition def) {
        this.syntaxDef = def != null ? def : new PlainTextSyntax();
        rehighlight();
    }

    public void setTheme(EditorTheme newTheme) {
        this.theme = newTheme != null ? newTheme : EditorTheme.DARK;
        applyTheme();
        rehighlight();
    }

    public EditorTheme getTheme() { return theme; }

    @Override
    public void clearMarkers() {
        markerRenderer.clearMarkers();
        applyParagraphStyles();
    }

    @Override
    public void addMarker(int line, MarkerType type, String message) {
        markerRenderer.addMarker(line, type, message);
        applyParagraphStyles();
    }

    @Override public boolean isDirty()               { return dirty.get(); }
    @Override public void clearDirty()               { dirty.set(false); }
    @Override public void requestFocus()             { codeArea.requestFocus(); }
    @Override public void cut()                      { codeArea.cut(); }
    @Override public void copy()                     { codeArea.copy(); }
    @Override public void paste()                    { codeArea.paste(); }
    @Override public void undo()                     { codeArea.undo(); }
    @Override public void redo()                     { codeArea.redo(); }
    @Override public ReadOnlyBooleanProperty dirtyProperty() { return dirty; }
    @Override public ReadOnlyStringProperty  textProperty()  { return textWrapper.getReadOnlyProperty(); }

    @Override
    public void showFindReplace() {
        ensureFindReplaceBar();
        findReplaceBar.setVisible(true);
        findReplaceBar.setManaged(true);
        findField.requestFocus();
    }

    @Override
    public void goToLine(int lineNumber) {
        if (lineNumber < 1) return;
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return;
        int physicalLine = syntaxDef.resolvePhysicalLine(text, lineNumber);
        int nParagraphs = codeArea.getText().split("\n", -1).length;
        int targetLine = Math.max(0, Math.min(physicalLine - 1, nParagraphs - 1));
        codeArea.moveTo(targetLine, 0);
        codeArea.requestFollowCaret();
        codeArea.requestFocus();
    }

    @Override public void setShowLineNumbers(boolean show) {
        codeArea.setParagraphGraphicFactory(show ? LineNumberFactory.get(codeArea) : null);
    }

    // ── Syntax highlighting ──────────────────────────────────────────────────

    private void rehighlight() {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            codeArea.setStyleSpans(0, emptySpans(0));
            applyParagraphStyles();
            return;
        }

        String[] lines = text.split("\n", -1);
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            List<StyleSpan> spans = syntaxDef.tokenize(line, lineIdx);

            if (spans.isEmpty()) {
                if (!line.isEmpty()) builder.add(styleClasses(TokenStyle.DEFAULT), line.length());
            } else {
                int cursor = 0;
                for (StyleSpan span : spans) {
                    int start = Math.max(cursor, Math.min(span.start(), line.length()));
                    int end   = Math.max(start,  Math.min(span.end(),   line.length()));
                    if (start > cursor) {
                        builder.add(styleClasses(TokenStyle.DEFAULT), start - cursor);
                    }
                    if (end > start) {
                        builder.add(styleClasses(span.style()), end - start);
                    }
                    cursor = end;
                }
                if (cursor < line.length()) {
                    builder.add(styleClasses(TokenStyle.DEFAULT), line.length() - cursor);
                }
            }

            // newline character between lines
            if (lineIdx < lines.length - 1) {
                builder.add(styleClasses(TokenStyle.DEFAULT), 1);
                pos += line.length() + 1;
            } else {
                pos += line.length();
            }
        }

        try {
            codeArea.setStyleSpans(0, builder.create());
        } catch (Exception ignored) {
            // text changed mid-highlight; next debounce cycle will retry
        }
        applyParagraphStyles();
    }

    private static Collection<String> styleClasses(TokenStyle style) {
        return Collections.singleton(switch (style) {
            case KEYWORD     -> "token-keyword";
            case STRING      -> "token-string";
            case COMMENT     -> "token-comment";
            case LINE_NUMBER -> "token-line-number";
            case LABEL       -> "token-label";
            case NUMBER      -> "token-number";
            case DIRECTIVE   -> "token-directive";
            default          -> "token-default";
        });
    }

    private static StyleSpans<Collection<String>> emptySpans(int len) {
        StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
        if (len > 0) b.add(Collections.emptyList(), len);
        else b.add(Collections.emptyList(), 0);
        return b.create();
    }

    // ── Paragraph marker styles ──────────────────────────────────────────────

    private void applyParagraphStyles() {
        var markers = markerRenderer.getMarkers();
        int nParagraphs = codeArea.getText().split("\n", -1).length;
        for (int i = 0; i < nParagraphs; i++) {
            int lineOneBased = i + 1;
            Collection<String> classes;
            if (markers.containsKey(lineOneBased)) {
                var list = markers.get(lineOneBased);
                boolean hasError = list.stream().anyMatch(m -> m.type() == MarkerType.ERROR);
                classes = Collections.singleton(hasError ? "marker-error" : "marker-warning");
            } else {
                classes = Collections.emptyList();
            }
            codeArea.setParagraphStyle(i, classes);
        }
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    private void applyTheme() {
        // Inline style on the CodeArea for background/text/selection
        codeArea.setStyle(
            "-fx-font-family: '" + FONT_FAMILY + "';"
            + "-fx-font-size: " + FONT_SIZE + "px;"
            + "-fx-background-color: " + toHex(theme.background) + ";"
            + "-fx-text-fill: " + toHex(theme.defaultFg) + ";");

        // Inject a scene stylesheet that maps token CSS classes to fill colors.
        // We use a data URI so there's no file I/O and no dependency on scene state.
        String css = buildThemeCss();
        // Store on the node's user data so we can remove the old one when theme changes
        Object old = getUserData();
        if (old instanceof String oldCss) {
            codeArea.getStylesheets().remove(oldCss);
        }
        String dataUri = "data:text/css," + java.net.URLEncoder.encode(css, java.nio.charset.StandardCharsets.UTF_8);
        codeArea.getStylesheets().add(dataUri);
        setUserData(dataUri);
    }

    private String buildThemeCss() {
        String bg  = toHex(theme.background);
        String fg  = toHex(theme.defaultFg);
        String sel = toHex(theme.selection);
        return ".code-area .text { -fx-fill: " + fg + "; }"
            + ".code-area { -fx-background-color: " + bg + "; }"
            + ".code-area .token-keyword     { -fx-fill: " + toHex(theme.keyword)    + "; }"
            + ".code-area .token-string      { -fx-fill: " + toHex(theme.string)     + "; }"
            + ".code-area .token-comment     { -fx-fill: " + toHex(theme.comment)    + "; }"
            + ".code-area .token-line-number { -fx-fill: " + toHex(theme.lineNumber) + "; }"
            + ".code-area .token-label       { -fx-fill: " + toHex(theme.label)      + "; }"
            + ".code-area .token-number      { -fx-fill: " + toHex(theme.number)     + "; }"
            + ".code-area .token-directive   { -fx-fill: " + toHex(theme.directive)  + "; }"
            + ".code-area .token-default     { -fx-fill: " + fg                      + "; }"
            + ".code-area .paragraph.marker-error   { -fx-background-color: rgba(255,50,50,0.15); }"
            + ".code-area .paragraph.marker-warning { -fx-background-color: rgba(255,200,0,0.15); }"
            + ".code-area .selection { -fx-fill: " + sel + "; }";
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
            (int) Math.round(c.getRed()   * 255),
            (int) Math.round(c.getGreen() * 255),
            (int) Math.round(c.getBlue()  * 255));
    }

    // ── Find/Replace ─────────────────────────────────────────────────────────

    private void ensureFindReplaceBar() {
        if (findReplaceBar != null) return;

        findField   = new TextField(); findField.setPromptText("Find");    findField.setPrefWidth(160);
        replaceField = new TextField(); replaceField.setPromptText("Replace"); replaceField.setPrefWidth(160);

        Button findNextBtn   = new Button("Find Next");  findNextBtn.setOnAction(e -> findNext());
        Button replaceBtn    = new Button("Replace");    replaceBtn.setOnAction(e -> replaceOne());
        Button replaceAllBtn = new Button("Replace All"); replaceAllBtn.setOnAction(e -> replaceAll());
        Button closeBtn      = new Button("✕");
        closeBtn.setOnAction(e -> {
            findReplaceBar.setVisible(false);
            findReplaceBar.setManaged(false);
            codeArea.requestFocus();
        });

        findField.setOnAction(e -> findNext());

        findReplaceBar = new HBox(4, findField, findNextBtn, replaceField, replaceBtn, replaceAllBtn, closeBtn);
        findReplaceBar.setAlignment(Pos.CENTER_LEFT);
        findReplaceBar.getStyleClass().add("find-replace-bar");
        findReplaceBar.setVisible(false);
        findReplaceBar.setManaged(false);
        getChildren().add(findReplaceBar);
    }

    private void findNext() {
        String needle = findField.getText();
        if (needle == null || needle.isEmpty()) return;
        String haystack = codeArea.getText();
        int from = codeArea.getCaretPosition();
        int idx = haystack.indexOf(needle, from);
        if (idx < 0) idx = haystack.indexOf(needle, 0);
        if (idx >= 0) codeArea.selectRange(idx, idx + needle.length());
    }

    private void replaceOne() {
        String needle = findField.getText();
        String replacement = replaceField.getText();
        if (needle == null || needle.isEmpty()) return;
        String selected = codeArea.getSelectedText();
        if (selected != null && selected.equals(needle)) {
            int start = codeArea.getSelection().getStart();
            int end   = codeArea.getSelection().getEnd();
            codeArea.replaceText(start, end, replacement != null ? replacement : "");
        }
        findNext();
    }

    private void replaceAll() {
        String needle = findField.getText();
        String replacement = replaceField.getText();
        if (needle == null || needle.isEmpty()) return;
        String updated = codeArea.getText().replace(needle, replacement != null ? replacement : "");
        codeArea.replaceText(updated);
    }

    // ── Package-accessible for testing ───────────────────────────────────────

    SyntaxDefinition getSyntaxDefinition() { return syntaxDef; }
    MarkerRenderer   getMarkerRenderer()   { return markerRenderer; }

    void appendTextForTest(String text)    { codeArea.appendText(text); }
    int  getCaretPositionForTest()         { return codeArea.getCaretPosition(); }
}
