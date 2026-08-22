package com.github.dropguard.summer.web.jsonb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvajeJsonbBodyConverterTest {

    private AvajeJsonbBodyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AvajeJsonbBodyConverter();
    }

    @Test
    void supportsContentType() {
        assertTrue(converter.supports("application/json"));
        assertTrue(converter.supports("application/json; charset=utf-8"));
        assertTrue(converter.supports("APPLICATION/JSON"));
        assertFalse(converter.supports("text/plain"));
        assertFalse(converter.supports("application/xml"));
        assertFalse(converter.supports(null));
    }

    @Test
    void getContentType() {
        assertEquals("application/json", converter.getContentType());
    }

    @Test
    void writeAndReadObject() throws IOException {
        PersonDto original = new PersonDto("Alice", 30, "alice@example.com");
        byte[] bytes = converter.write(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"age\":30"));
        assertTrue(json.contains("\"email\":\"alice@example.com\""));

        PersonDto deserialized = converter.read(bytes, PersonDto.class);
        assertEquals(original, deserialized);
    }

    @Test
    void writeToStream() throws IOException {
        PersonDto original = new PersonDto("Bob", 25, "bob@example.com");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.writeToStream(original, out);

        byte[] directBytes = converter.write(original);
        assertArrayEquals(directBytes, out.toByteArray());
    }

    @Test
    void nullHandling() throws IOException {
        byte[] bytes = converter.write(null);
        assertEquals(0, bytes.length);

        PersonDto deserialized = converter.read(null, PersonDto.class);
        assertNull(deserialized);

        PersonDto emptyDeserialized = converter.read(new byte[0], PersonDto.class);
        assertNull(emptyDeserialized);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.writeToStream(null, out);
        assertEquals(0, out.size());
    }
}
