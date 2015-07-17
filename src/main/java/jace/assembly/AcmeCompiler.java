package jace.assembly;

import jace.ide.Program;
import jace.ide.CompileResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author blurry
 */
public class AcmeCompiler implements CompileResult<File> {

    boolean successful = false;
    File compiledAsset = null;
    Map<Integer, String> errors = new LinkedHashMap<>();
    Map<Integer, String> warnings = new LinkedHashMap<>();
    List<String> otherWarnings = new ArrayList<>();
    List<String> rawOutput = new ArrayList<>();

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public File getCompiledAsset() {
        return compiledAsset;
    }

    @Override
    public Map<Integer, String> getErrors() {
        return errors;
    }

    @Override
    public Map<Integer, String> getWarnings() {
        return warnings;
    }

    @Override
    public List<String> getOtherMessages() {
        return otherWarnings;
    }

    @Override
    public List<String> getRawOutput() {
        return rawOutput;
    }

    public void compile(Program proxy) {
        File workingDirectory = proxy.getFile()
                .map(file -> file.getParentFile())
                .orElse(new File(System.getProperty("user.dir")));
        File sourceFile = new File(workingDirectory, "_acme_tmp_" + ((int) (Math.random() * 1024.0)) + ".a");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.append(proxy.getValue());
            writer.flush();
            writer.close();
            invokeAcme(sourceFile, workingDirectory);
        } catch (Exception ex) {
            compilationFailure(ex);
        } finally {
            sourceFile.delete();            
        }
    }

    private void compilationFailure(Exception ex) {
        Logger.getLogger(AcmeCompiler.class.getName()).log(Level.SEVERE, null, ex);
        rawOutput = Arrays.asList(ex.getStackTrace()).stream().map(element -> element.toString()).collect(Collectors.toList());
        otherWarnings = rawOutput;
    }

    PrintStream systemOut = System.out;
    PrintStream systemErr = System.err;
    ByteArrayOutputStream baosOut;
    PrintStream out;
    ByteArrayOutputStream baosErr;
    PrintStream err;

    private void invokeAcme(File sourceFile, File workingDirectory) throws ClassNotFoundException, SecurityException, NoSuchMethodException, IOException {
        String oldPath = System.getProperty("user.dir");
        redirectSystemOutput();
        try {
            compiledAsset = File.createTempFile(sourceFile.getName(), "bin", sourceFile.getParentFile());
            System.setProperty("user.dir", workingDirectory.getAbsolutePath());
            AcmeCrossAssembler acme = new AcmeCrossAssembler();
            String[] params = {"--outfile", compiledAsset.getAbsolutePath(), "-f", "cbm", "--maxerrors","16",sourceFile.getAbsolutePath()};
            int status = acme.run("Acme", params);
            successful = status == 0;
            if (!successful) {
                compiledAsset.delete();
                compiledAsset = null;
            }
        } finally {
            restoreSystemOutput();
            System.setProperty("user.dir", oldPath);
        }
        rawOutput.add("Error output:");
        extractOutput(baosErr.toString());
        rawOutput.add("");
        rawOutput.add("------------------------------");
        rawOutput.add("Standard output:");
        extractOutput(baosOut.toString());
    }

    public void extractOutput(String output) throws NumberFormatException {
        for (String line : output.split("\\n")) {
            rawOutput.add(line);
            int lineNumberStart = line.indexOf(", line") + 6;
            if (lineNumberStart > 6) {
                int lineNumberEnd = line.indexOf(' ', lineNumberStart+1);
                int actualLineNumber = Integer.parseUnsignedInt(line.substring(lineNumberStart, lineNumberEnd).trim());
                String message = line.substring(lineNumberEnd).trim();
                if (line.startsWith("Error")) {
                    errors.put(actualLineNumber, message);
                } else {
                    warnings.put(actualLineNumber, message);
                }
            } else {
                if (line.trim().length() > 1) {
                    otherWarnings.add(line);
                }
            }
        }
    }

    public void restoreSystemOutput() {
        System.setOut(systemOut);
        System.setErr(systemErr);
    }

    public void redirectSystemOutput() {
        baosOut = new ByteArrayOutputStream();
        out = new PrintStream(baosOut);
        baosErr = new ByteArrayOutputStream();
        err = new PrintStream(baosErr);
        System.setOut(out);
        System.setErr(err);
    }
}
