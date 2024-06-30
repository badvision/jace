package jace.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

@SuppressWarnings("all")
public abstract class InvokableActionRegistry {
    protected static final Logger logger = Logger.getLogger(InvokableActionRegistry.class.getName());
    private final Map<Class, Set<String>> staticMethodNames = new HashMap<>();
    private final Map<String, InvokableAction> staticMethodInfo = new HashMap<>();
    private final Map<String, Function<Boolean, Boolean>> staticMethodCallers = new HashMap<>();
    private final Map<Class, Set<String>> instanceMethodNames = new HashMap<>();
    private final Map<String, InvokableAction> instanceMethodInfo = new HashMap<>();
    private final Map<String, BiFunction<Object, Boolean, Boolean>> instanceMethodCallers = new HashMap<>();

    protected static InvokableActionRegistry instance;

    public static InvokableActionRegistry getInstance() {
        if (instance == null) {
            instance = new InvokableActionRegistryImpl();
            instance.init();
        }
        return instance;
    }


    abstract public void init();

    final public void putStaticAction(String name, Class c, InvokableAction action, Consumer<Boolean> caller) {
        putStaticAction(name, c, action, (b) -> {
            caller.accept(b);
            return true;
        });
    }

    final public void putStaticAction(String name, Class c, InvokableAction action, Function<Boolean, Boolean> caller) {
        staticMethodInfo.put(name, action);
        staticMethodCallers.put(name, caller);
        staticMethodNames.computeIfAbsent(c, k -> new TreeSet<>()).add(name);
    }

    public void putInstanceAction(String name, Class c, InvokableAction action, BiConsumer<Object, Boolean> caller) {
        putInstanceAction(name, c, action, (o, b) -> {
            caller.accept(o, b);
            return true;
        });
    }

    public void putInstanceAction(String name, Class c, InvokableAction action, BiFunction<Object, Boolean, Boolean> caller) {
        instanceMethodInfo.put(name, action);
        instanceMethodCallers.put(name, caller);
        instanceMethodNames.computeIfAbsent(c, k -> new TreeSet<>()).add(name);
    }

    public Set<String> getStaticMethodNames(Class c) {
        // Build a set of all the method names for this class and all its superclasses.
        Set<String> result = new TreeSet<>();
        Class current = c;
        while (current != null) {
            result.addAll(staticMethodNames.getOrDefault(current, Collections.EMPTY_SET));
            current = current.getSuperclass();
        }
        return result;
    }

    public Set<String> getInstanceMethodNames(Class c) {
        // Build a set of all the method names for this class and all its superclasses.
        Set<String> result = new TreeSet<>();
        Class current = c;
        while (current != null) {
            result.addAll(instanceMethodNames.getOrDefault(current, Collections.EMPTY_SET));
            current = current.getSuperclass();
        }
        return result;
    }

    public InvokableAction getStaticMethodInfo(String name) {
        return staticMethodInfo.get(name);
    }

    public InvokableAction getInstanceMethodInfo(String name) {
        return instanceMethodInfo.get(name);
    }

    public Function<Boolean, Boolean> getStaticFunction(String name) {
        return staticMethodCallers.get(name);
    }

    public BiFunction<Object, Boolean, Boolean> getInstanceFunction(String name) {
        return instanceMethodCallers.get(name);
    }

    public Set<InvokableAction> getAllStaticActions() {
        return new HashSet<>(staticMethodInfo.values());        
    }

    protected InvokableAction createInvokableAction(String name, String category, String description, String alternatives, boolean consumeKeyEvent, boolean notifyOnRelease, String[] defaultKeyMapping) {
        return new InvokableAction() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String alternatives() {
                return alternatives;
            }

            @Override
            public boolean consumeKeyEvent() {
                return consumeKeyEvent;
            }

            @Override
            public boolean notifyOnRelease() {
                return notifyOnRelease;
            }

            @Override
            public String[] defaultKeyMapping() {
                return defaultKeyMapping;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return InvokableAction.class;
            }
        };
    }   
}