package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ResponseSerializationTest {

    private final JsonBodyConverter converter = new JsonBodyConverter();

    @Test
    public void testJsonSerializationWithJavaTimeTypes() throws IOException {
        Map<String, Object> testData = new HashMap<>();
        testData.put("stringValue", "Hello World");
        testData.put("intValue", 42);
        testData.put("booleanValue", true);
        testData.put("localDate", LocalDate.now());
        testData.put("localDateTime", LocalDateTime.now());
        testData.put("zonedDateTime", ZonedDateTime.now());

        byte[] result = converter.write(testData);

        assertNotNull(result, "Serialized bytes should not be null");
        assertTrue(result.length > 0, "Serialized bytes should not be empty");

        String json = new String(result, StandardCharsets.UTF_8);
        // Verify JSON contains expected keys (INDENT_OUTPUT may add whitespace)
        assertTrue(json.contains("stringValue"), "Should contain stringValue key");
        assertTrue(json.contains("intValue"), "Should contain intValue key");
        assertTrue(json.contains("booleanValue"), "Should contain boolean value key");
        assertTrue(json.contains("localDate"), "Should contain localDate key");
        assertTrue(json.contains("localDateTime"), "Should contain localDateTime key");
        assertTrue(json.contains("zonedDateTime"), "Should contain zonedDateTime key");
    }

    @Test
    public void testJsonSerializationWithCollections() throws IOException {
        List<Map<String, Object>> listData = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Item 1");
        item1.put("value", 10);
        listData.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Item 2");
        item2.put("value", 20);
        listData.add(item2);

        byte[] result = converter.write(listData);

        assertNotNull(result, "Serialized bytes should not be null");
        assertTrue(result.length > 0, "Serialized bytes should not be empty");

        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("Item 1"), "Should contain first item name");
        assertTrue(json.contains("Item 2"), "Should contain second item name");
    }

    @Test
    public void testJsonSerializationWithNullValues() throws IOException {
        Map<String, Object> testData = new HashMap<>();
        testData.put("nullValue", null);
        testData.put("validValue", "Some Value");

        byte[] result = converter.write(testData);

        assertNotNull(result, "Serialized bytes should not be null");
        assertTrue(result.length > 0, "Serialized bytes should not be empty");

        String json = new String(result, StandardCharsets.UTF_8);
        assertTrue(json.contains("nullValue"), "Should contain nullValue key");
        assertTrue(json.contains("validValue"), "Should contain validValue key");
    }
}
