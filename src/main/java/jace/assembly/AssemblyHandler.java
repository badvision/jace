package jace.assembly;

import java.nio.ByteBuffer;

import jace.Emulator;
import jace.core.Computer;
import jace.core.RAM;
import jace.ide.CompileResult;
import jace.ide.HeadlessProgram;
import jace.ide.LanguageHandler;
import jace.ide.Program;

/**
 *
 * @author blurry
 */
public class AssemblyHandler implements LanguageHandler<ByteBuffer> {
    @Override
    public String getNewDocumentContent() {
        return "\t\t*= $300;\n\t\t!cpu 65c02;\n;--- Insert your code here ---\n";
    }    

    @Override
    public CompileResult<ByteBuffer> compile(Program proxy) {
        AcmeCompiler compiler = new AcmeCompiler();
        compiler.compile(proxy);
        return compiler;
    }

    public void compileToRam(String code) {
        HeadlessProgram prg = new HeadlessProgram(Program.DocumentType.assembly);
        prg.setValue(code);
        
        CompileResult<ByteBuffer> lastResult = compile(prg);
        if (lastResult.isSuccessful()) {
            Emulator.withComputer(c -> {
                RAM memory = c.getMemory();
                ByteBuffer input = lastResult.getCompiledAsset();
                input.rewind();
                int startLSB = input.get();
                int startMSB = input.get();
                int start = startLSB + startMSB << 8;
                System.out.printf("Storing assembled code to $%s%n", Integer.toHexString(start));
                c.getCpu().whileSuspended(() -> {
                    int pos = start;
                    while (input.hasRemaining()) {
                        memory.write(pos++, input.get(), false, true);
                    }
                });
            });
        }
    }
    
    @Override
    public void writeToMemory(CompileResult<ByteBuffer> lastResult) throws Exception {
        if (!lastResult.isSuccessful()) {
            throw new Exception("Compilation failed");
        }
        Computer c = Emulator.withComputer(c1 -> c1, null);
        RAM memory = c.getMemory();
        ByteBuffer input = lastResult.getCompiledAsset();
        input.rewind();
        int startLSB = input.get() & 0x0ff;
        int startMSB = input.get() & 0x0ff;
        int start = startLSB + startMSB << 8;
        c.getCpu().whileSuspended(() -> {
            int pos = start;
            while (input.hasRemaining()) {
                memory.write(pos++, input.get(), false, true);
            }
        });
    }

    @Override
    public void execute(CompileResult<ByteBuffer> lastResult) throws Exception {
        if (!lastResult.isSuccessful()) {
            System.err.println("Compilation failed");
            lastResult.getErrors().forEach((line, message) -> System.err.printf("Line %d: %s%n", line, message));
            lastResult.getOtherMessages().forEach(System.err::println);
            throw new Exception("Compilation failed");
        }
        ByteBuffer input = lastResult.getCompiledAsset();
        input.rewind();
        int start = (input.get() & 0xff) | ((input.get() & 0xff) << 8);
        writeToMemory(lastResult);
        Computer c = Emulator.withComputer(c1 -> c1, null);
        c.getCpu().whileSuspended(() -> c.getCpu().JSR(start));
        clean(lastResult);
    }

    @Override
    public void clean(CompileResult<ByteBuffer> lastResult) {
        // Nothing to do here
    }
}
