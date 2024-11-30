package jace.core;

public class BooleanDeserializer implements ValueDeserializer {
    @Override
    public Object deserialize(String value) {
        return Integer.parseInt(value);
    }
    
    @Override
    public boolean canHandle(Class type) {
        return type.equals(Integer.class) || type.equals(Integer.TYPE);
    }
}
