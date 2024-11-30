package jace.core;

public interface ValueDeserializer {
    Object deserialize(String value);
    boolean canHandle(Class type);
}