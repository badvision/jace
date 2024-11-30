package jace.core;

import java.util.Arrays;
import java.util.List;

public class DeserializerFactory {
    private List<ValueDeserializer> deserializers;
    
    public DeserializerFactory() {
        deserializers = Arrays.asList(
            new IntegerDeserializer(),
            new BooleanDeserializer(),
            new DoubleDeserializer()
        );
    }
    
    public ValueDeserializer getDeserializer(Class type) {
        return deserializers.stream()
            .filter(d -> d.canHandle(type))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No deserializer for type " + type));
    }
}