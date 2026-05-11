package summer.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import summer.core.Component;

/**
 * Standard JSON implementation of BodyConverter using Jackson.
 */
@Component
public class JsonBodyConverter implements BodyConverter {
	private static final ObjectMapper objectMapper;

	static {
		objectMapper = new ObjectMapper();
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		JavaTimeModule javaTimeModule = new JavaTimeModule();
		javaTimeModule.addSerializer(java.time.LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ISO_DATE));
		javaTimeModule.addSerializer(java.time.LocalDateTime.class,
				new LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME));
		javaTimeModule.addSerializer(java.time.ZonedDateTime.class,
				new ZonedDateTimeSerializer(DateTimeFormatter.ISO_ZONED_DATE_TIME));
		objectMapper.registerModule(javaTimeModule);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
	public String getContentType() {
		return "application/json";
	}
}
