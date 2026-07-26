package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class JsonBodyConverterTest {

	@Test
	void shouldThrowExceptionOnSerializationFailure() {
		JsonBodyConverter converter = new JsonBodyConverter();

		// Create an object that Jackson cannot serialize (e.g. self-referencing)
		class BadObject {
			public BadObject self = this;
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
				() -> converter.writeToStream(new BadObject(), out));
	}
}
