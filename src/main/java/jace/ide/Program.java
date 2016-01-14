package jace.ide;

import jace.applesoft.ApplesoftHandler;
import jace.assembly.AssemblyHandler;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.TextInputDialog;
import javafx.scene.web.PromptData;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/**
 *
 * @author blurry
 */
public class Program {

    public static String CODEMIRROR_EDITOR = "/codemirror/editor.html";

    public static enum DocumentType {

        applesoft(new ApplesoftHandler(), "textfile", "*.bas"), assembly(new AssemblyHandler(), "textfile", "*.a", "*.s", "*.asm"), plain(new TextHandler(), "textfile", "*.txt"), hex(new TextHandler(), "textfile", "*.bin", "*.raw");

        static DocumentType fromFile(File file) {
            String name = file.getName();
            String ext = name.substring(name.lastIndexOf('.')).toLowerCase();
            ext = "*" + ext;
            for (DocumentType type : values()) {
                if (type.extensions.contains(ext)) {
                    return type;
                }
            }
            return DocumentType.plain;
        }
        String modeName;
        LanguageHandler languageHandler;
        List<String> extensions;

        DocumentType(LanguageHandler handler, String mode, String... defaultExtensions) {
            languageHandler = handler;
            modeName = mode;
            extensions = Arrays.asList(defaultExtensions);
        }
    }

    public static enum Option {

        mode, value, theme, indentUnit, smartIndent, tabSize, indentWithTabs, electricChars, specialChars,
        specialCharPlaceHolder, rtlMoveVisually, keyMap, extraKeys, lineWrapping,
        lineNumbers, firstLineNumber, lineNumberFormatter, gutters, fixedGutter,
        scrollbarStyle, coverGutterNextToScrollbar, inputStyle, readOnly, showCursorWhenSelecting,
        lineWiseCopyCut, undoDepth, historyEventDelay, tabindex, autofocus
    }

    DocumentType fileType;
    boolean showLineNumbers;
    Map<Option, Object> options;
    File targetFile = null;
    String filename = "Untitled File";
    JSObject codeMirror;
    WebView editor;
    CompileResult lastResult;

    public Program(DocumentType type, Map<Option, Object> globalOptions) {
        fileType = type;
        options = new EnumMap<>(Option.class);
        options.putAll(globalOptions);
        options.put(Option.mode, fileType.modeName);
        options.put(Option.lineNumbers, true);
    }

    public String getName() {
        return filename;
    }

    public DocumentType getType() {
        return fileType;
    }

    public LanguageHandler getHandler() {
        return fileType.languageHandler;
    }

    public String getValue() {
        return (String) codeMirror.call("getValue");
    }

    public void setValue(String value) {
        codeMirror.call("setValue", value);
    }

    public Optional<File> getFile() {
        return Optional.ofNullable(targetFile);
    }

    public void initEditor(WebView editor, File sourceFile) {
        this.editor = editor;
        targetFile = sourceFile;
        if (targetFile != null) {
            filename = targetFile.getName();
        }

        editor.getEngine().getLoadWorker().stateProperty().addListener(
                (value, old, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        JSObject document = (JSObject) editor.getEngine().executeScript("window");
                        document.setMember("java", this);
                        Platform.runLater(this::createEditor);
                    }
                });

        editor.getEngine().setPromptHandler((PromptData prompt) -> {
            TextInputDialog dialog = new TextInputDialog(prompt.getDefaultValue());
            dialog.setTitle("Jace IDE");
            dialog.setHeaderText("Respond and press OK, or Cancel to abort");
            dialog.setContentText(prompt.getMessage());
            return dialog.showAndWait().orElse(null);
        });

        editor.getEngine().load(getClass().getResource(CODEMIRROR_EDITOR).toExternalForm());
    }

    public void createEditor() {
        String document = targetFile == null ? getHandler().getNewDocumentContent() : getFileContents(targetFile);
        String optionString = buildOptions();
        editor.getEngine().executeScript("var codeMirror = CodeMirror(document.body, " + optionString + ");");
        codeMirror = (JSObject) editor.getEngine().executeScript("codeMirror");
        Platform.runLater(() -> setValue(document));
//        setValue(document);
    }

    public String getFileContents(File sourceFile) {
        if (sourceFile != null && sourceFile.exists() && sourceFile.isFile()) {
            BufferedReader reader = null;
            try {
                StringBuilder input = new StringBuilder();
                reader = new BufferedReader(new FileReader(sourceFile));
                reader.lines().forEach((line) -> {
                    input.append(line);
                    input.append("\n");
                });
                return input.toString();
            } catch (IOException ex) {
                Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return "";
    }

    public void save(File newTarget) {
        FileWriter writer = null;
        if (newTarget == null && targetFile == null) {
            return;
        }
        if (newTarget != null) {
            targetFile = newTarget;
        }
        filename = targetFile.getName();
        try {
            writer = new FileWriter(targetFile, false);
            writer.append(getValue());
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean isChanged() {
        return (Boolean) codeMirror.call("isClean");
    }

    private String buildOptions() {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        options.forEach((Option o, Object v) -> {
            if (builder.length() >= 2) {
                builder.append(",\n");
            }
            builder.append(o.name()).append(":");
            if (v instanceof String) {
                builder.append('"').append(String.valueOf(v)).append('"');
            } else {
                builder.append(String.valueOf(v));
            }
        });
        builder.append("}");
        return builder.toString();
    }

    public void execute() {
        lastResult = getHandler().compile(this);
        manageCompileResult(lastResult);
        if (lastResult.isSuccessful()) {
            getHandler().execute(lastResult);
        } else {
            getHandler().clean(lastResult);
        }
    }

    public void test() {
        lastResult = getHandler().compile(this);
        manageCompileResult(lastResult);
        getHandler().clean(lastResult);
    }

    private void manageCompileResult(CompileResult lastResult) {
        editor.getEngine().executeScript("clearHighlights()");
        lastResult.getWarnings().forEach((line, message)
                -> editor.getEngine().executeScript("highlightLine(" + line + ",false,\"" + escapeString(message) + "\");")
        );
        lastResult.getErrors().forEach((line, message)
                -> editor.getEngine().executeScript("highlightLine(" + line + ",true,\"" + escapeString(message) + "\");")
        );
    }

    private String escapeString(Object message) {
        return String.valueOf(message).replaceAll("\\\"", "&quot;");
    }

    public void log(String message) {
        System.out.println(message);
    }
}
