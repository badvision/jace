package jace.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

// Compile-time annotation processor which creates a registry of all static methods annotated with @InvokableAction.
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("jace.config.InvokableAction")
public class InvokableActionAnnotationProcessor extends AbstractProcessor {
    Messager messager;
    Map<InvokableAction, ExecutableElement> staticMethods = new HashMap<>();
    Map<InvokableAction, ExecutableElement> instanceMethods = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        messager.printMessage(javax.tools.Diagnostic.Kind.NOTE, "InvokableActionAnnotationProcessor init()");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager.printMessage(javax.tools.Diagnostic.Kind.NOTE, "InvokableActionAnnotationProcessor process()");

        // Get list of methods annotated with @InvokableAction.
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(InvokableAction.class);
        for (Element element : elements) {
            if (element.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) {
                // If the annotation method is static, add it to the static method registry.
                trackStaticMethod(element);
            } else {
                // For non-static methods, track in a separate registry.
                trackInstanceMethod(element);
            }
            try {
                // Write class that contains static and instance methods.
                writeRegistryClass();
            } catch (IOException ex) {
                messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, "Error writing InvokableActionRegistry.java: " + ex.getMessage());
            }
        }
        return true;
    }

    private void trackStaticMethod(Element element) {
        // Store the method in the static method registry.
        staticMethods.put(element.getAnnotation(InvokableAction.class), (ExecutableElement) element);
    }

    private void trackInstanceMethod(Element element) {
        // Store the method in the instance method registry.
        instanceMethods.put(element.getAnnotation(InvokableAction.class), (ExecutableElement) element);
    }

    private String serializeArrayOfStrings(String... strings) {
        return Arrays.stream(strings).map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
    }

    private void serializeInvokableAction(InvokableAction annotation, String variableName, PrintWriter writer) {
        writer.append("""
                %s = createInvokableAction("%s", "%s", "%s", "%s", %s, %s, new String[] {%s});
        """.formatted(
            variableName, 
            annotation.name(),
            annotation.category(),
            annotation.description(),
            annotation.alternatives(), 
            annotation.consumeKeyEvent(),
            annotation.notifyOnRelease(),
            serializeArrayOfStrings(annotation.defaultKeyMapping())
        ));
    }

    // Write the registry class.
    private void writeRegistryClass() throws IOException {
        Files.createDirectories(new File("target/generated-sources/jace/config").toPath());
        try (PrintWriter writer = new PrintWriter(new FileWriter("target/generated-sources/jace/config/InvokableActionRegistryImpl.java"))) {
            writer.write("""
package jace.config;

import java.util.logging.Level;
                
public class InvokableActionRegistryImpl extends InvokableActionRegistry {
    @Override
    public void init() {
        InvokableAction annotation;
""");
            for (Map.Entry<InvokableAction, ExecutableElement> entry : staticMethods.entrySet()) {
                InvokableAction annotation = entry.getKey();
                ExecutableElement method = entry.getValue();
                String packageName = method.getEnclosingElement().getEnclosingElement().toString();
                String className = method.getEnclosingElement().getSimpleName().toString();
                String fqnClassName = packageName + "." + className;
                serializeInvokableAction(annotation, "annotation", writer);
                boolean takesBoolenParameter = method.getParameters().size() == 1 && method.getParameters().get(0).asType().toString().equalsIgnoreCase("boolean");
                boolean returnsBoolean = method.getReturnType().toString().equalsIgnoreCase("boolean");
                writer.write("""
                        putStaticAction(annotation.name(), %s.class, annotation, (b) -> {
                            try {
                                %s %s.%s(%s);
                            } catch (Exception ex) {
                                logger.log(Level.SEVERE, "Error invoking %s", ex);
                                %s
                            }
                        });
                """.formatted(
                    fqnClassName,
                    returnsBoolean ? "return " : "",
                    fqnClassName, 
                    method.getSimpleName(), 
                    takesBoolenParameter ? "b" : "",
                    fqnClassName + "." + method.getSimpleName(),
                    returnsBoolean ? "return false;" : ""
                ));
            }

            // Now for the instance methods, do the same except use a biconsumer which takes the instance as well as the boolean parameter.
            for (Map.Entry<InvokableAction, ExecutableElement> entry : instanceMethods.entrySet()) {
                InvokableAction annotation = entry.getKey();
                ExecutableElement method = entry.getValue();
                String packageName = method.getEnclosingElement().getEnclosingElement().toString();
                String className = method.getEnclosingElement().getSimpleName().toString();
                String fqnClassName = packageName + "." + className;
                serializeInvokableAction(annotation, "annotation", writer);
                boolean takesBoolenParameter = method.getParameters().size() == 1 && method.getParameters().get(0).asType().toString().equalsIgnoreCase("boolean");
                boolean returnsBoolean = method.getReturnType().toString().equalsIgnoreCase("boolean");
                writer.write("""
                        putInstanceAction(annotation.name(), %s.class, annotation, (o, b) -> {
                            try {
                                %s ((%s) o).%s(%s);
                            } catch (Exception ex) {
                                logger.log(Level.SEVERE, "Error invoking %s", ex);
                                %s
                            }
                        });
                """.formatted(
                    fqnClassName,
                    returnsBoolean ? "return " : "",
                    fqnClassName,
                    method.getSimpleName(), 
                    takesBoolenParameter ? "b" : "",
                    fqnClassName + "." + method.getSimpleName(),
                    returnsBoolean ? "return false;" : ""
                ));
            }
            writer.write("}\n}");
        }
    }
}