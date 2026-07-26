package com.github.dropguard.summer.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Standard JSON implementation of BodyConverter using Jackson.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@code WebInfrastructureConfiguration}.
 * </p>
 */
public class JsonBodyConverter implements BodyConverter {
	private static final ObjectMapper objectMapper;

	static {
		objectMapper = SummerObjectMapper.create(m -> {
			m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
			m.configure(SerializationFeature.INDENT_OUTPUT, true);

			var javaTimeModule = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
			javaTimeModule.addSerializer(java.time.LocalDate.class,
					new LocalDateSerializer(DateTimeFormatter.ISO_DATE));
			javaTimeModule.addSerializer(java.time.LocalDateTime.class,
					new LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME));
			javaTimeModule.addSerializer(java.time.ZonedDateTime.class,
					new ZonedDateTimeSerializer(DateTimeFormatter.ISO_ZONED_DATE_TIME));
			m.registerModule(javaTimeModule);

			m.setSerializationInclusion(JsonInclude.Include.ALWAYS);
		});
	}

	@Override
	public boolean supports(String contentType) {
		return contentType != null && contentType.toLowerCase().contains("application/json");
	}

	@Override
	public <T> T read(byte[] body, Class<T> type) throws IOException {
		return objectMapper.readValue(body, type);
	}

	@Override
	public byte[] write(Object content) throws IOException {
		return objectMapper.writeValueAsBytes(content);
	}

	@Override
	public void writeToStream(Object content, java.io.OutputStream out) throws IOException {
		objectMapper.writeValue(out, content);
	}

	@Override
	public String getContentType() {
		return "application/json";
	}
}
