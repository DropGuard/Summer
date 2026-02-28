package summer.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Handles JSON serialization and deserialization.
 * Centralizes Jackson configuration to decouple HTTP Models (Request/Response)
 * from the underlying JSON engine.
 */
public class JsonConverter {
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

    public static String toJson(Object content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(content);
    }

    public static <T> T fromJson(byte[] body, Class<T> type) throws IOException {
        return objectMapper.readValue(body, type);
    }
}
