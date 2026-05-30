package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ResponseSerializationTest {

	@Test
	public void testJsonSerializationWithJavaTimeTypes() {
		Map<String, Object> testData = new HashMap<>();
		testData.put("stringValue", "Hello World");
		testData.put("intValue", 42);
		testData.put("booleanValue", true);
		testData.put("localDate", LocalDate.now());
		testData.put("localDateTime", LocalDateTime.now());
		testData.put("zonedDateTime", ZonedDateTime.now());

		try {
			new JsonBodyConverter().write(testData);
			assertTrue(true);
		} catch (Exception e) {
			fail("Serialization failed: " + e.getMessage());
		}
	}

	@Test
	public void testJsonSerializationWithCollections() {
		List<Map<String, Object>> listData = new ArrayList<>();
		Map<String, Object> item1 = new HashMap<>();
		item1.put("name", "Item 1");
		item1.put("value", 10);
		listData.add(item1);

		Map<String, Object> item2 = new HashMap<>();
		item2.put("name", "Item 2");
		item2.put("value", 20);
		listData.add(item2);

		try {
			new JsonBodyConverter().write(listData);
			assertTrue(true);
		} catch (Exception e) {
			fail("Collection serialization failed: " + e.getMessage());
		}
	}

	@Test
	public void testJsonSerializationWithNullValues() {
		Map<String, Object> testData = new HashMap<>();
		testData.put("nullValue", null);
		testData.put("validValue", "Some Value");

		try {
			new JsonBodyConverter().write(testData);
			assertTrue(true);
		} catch (Exception e) {
			fail("Serialization with null values failed: " + e.getMessage());
		}
	}
}
