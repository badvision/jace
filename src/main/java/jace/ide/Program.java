package jace.ide;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jace.applesoft.ApplesoftHandler;
import jace.assembly.AssemblyHandler;

/**
 *
 * @author blurry
 */
public class Program {

    public enum DocumentType {

        applesoft(new ApplesoftHandler(), "*.bas"),
        assembly(new AssemblyHandler(), "*.a", "*.s", "*.asm"),
        plain(new TextHandler(), "*.txt"),
        hex(new TextHandler(), "*.bin", "*.raw");

        static DocumentType fromFile(File file) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot < 0) return DocumentType.plain;
            String ext = "*" + name.substring(dot).toLowerCase();
            for (DocumentType type : values()) {
                if (type.extensions.contains(ext)) {
                    return type;
                }
            }
            return DocumentType.plain;
        }

        LanguageHandler languageHandler;
        List<String> extensions;

        DocumentType(LanguageHandler handler, String... defaultExtensions) {
            languageHandler = handler;
            extensions = Arrays.asList(defaultExtensions);
        }
    }

    DocumentType fileType;
    File targetFile = null;
    String filename = "Untitled File";
    EditorControl editorControl;
    CompileResult lastResult;

    public Program(DocumentType type) {
        fileType = type;
    }

    public void initEditor(EditorControl control, File sourceFile, boolean isBlank) {
        this.editorControl = control;
        targetFile = sourceFile;
        if (targetFile != null) {
            filename = targetFile.getName();
        }
        String content = targetFile == null
                ? (isBlank ? "" : getHandler().getNewDocumentContent())
                : getFileContents(targetFile);
        control.setText(content);
        control.setSyntaxDefinition(syntaxForType(fileType));
    }

    private SyntaxDefinition syntaxForType(DocumentType type) {
        return switch (type) {
            case applesoft -> new AppleSoftBasicSyntax();
            case assembly  -> new MOS65C02AssemblySyntax();
            default        -> new PlainTextSyntax();
        };
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
        return editorControl != null ? editorControl.getText() : "";
    }

    public void setValue(String value) {
        if (editorControl != null) editorControl.setText(value);
    }

    public Optional<File> getFile() {
        return Optional.ofNullable(targetFile);
    }

    public String getFileContents(File sourceFile) {
        if (sourceFile != null && sourceFile.exists() && sourceFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
                StringBuilder input = new StringBuilder();
                reader.lines().forEach(line -> input.append(line).append("\n"));
                return input.toString();
            } catch (IOException ex) {
                Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return "";
    }

    public void save(File newTarget) {
        if (newTarget == null && targetFile == null) return;
        if (newTarget != null) targetFile = newTarget;
        filename = targetFile.getName();
        try (FileWriter writer = new FileWriter(targetFile, false)) {
            writer.append(getValue());
        } catch (IOException ex) {
            Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean isChanged() {
        return editorControl != null && editorControl.isDirty();
    }

    public void execute() throws Exception {
        lastResult = getHandler().compile(this);
        manageCompileResult(lastResult);
        if (lastResult.isSuccessful()) {
            getHandler().execute(lastResult);
        } else {
            StringBuilder error = new StringBuilder("Compilation failed:\n");
            lastResult.getErrors().forEach((line, message) -> error.append("Line %d: %s%n".formatted(line, message)));
            lastResult.getOtherMessages().forEach(message -> error.append(message).append("\n"));
            getHandler().clean(lastResult);
            throw new Exception(error.toString());
        }
    }

    public void build() throws Exception {
        lastResult = getHandler().compile(this);
        manageCompileResult(lastResult);
        if (lastResult.isSuccessful()) {
            getHandler().writeToMemory(lastResult);
        } else {
            StringBuilder error = new StringBuilder("Compilation failed:\n");
            lastResult.getErrors().forEach((line, message) -> error.append("Line %d: %s%n".formatted(line, message)));
            lastResult.getOtherMessages().forEach(message -> error.append(message).append("\n"));
            getHandler().clean(lastResult);
            throw new Exception(error.toString());
        }
    }

    @SuppressWarnings("unchecked")
    protected void manageCompileResult(CompileResult lastResult) {
        if (editorControl == null) return;
        editorControl.clearMarkers();
        lastResult.getWarnings().forEach((line, message)
                -> editorControl.addMarker((Integer) line, MarkerType.WARNING, String.valueOf(message)));
        lastResult.getErrors().forEach((line, message)
                -> editorControl.addMarker((Integer) line, MarkerType.ERROR, String.valueOf(message)));
    }
}
