package jace.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DeserializerTest {
    @Test
void testIntegerDeserializer() {
    ValueDeserializer deserializer = new IntegerDeserializer();
    assertEquals(42, deserializer.deserialize("42"));
}
}
