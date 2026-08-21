package com.github.dropguard.summer.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Standard JSON implementation of BodyConverter using Jackson.
 *
 * <p>This is a framework infrastructure bean provided by {@code WebInfrastructureConfiguration}.
 */
@Internal
public class JsonBodyConverter implements BodyConverter {
    private static final ObjectMapper objectMapper;

    static {
        objectMapper =
                SummerObjectMapper.create(
                        m -> {
                            m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                            // Deliberate API-response defaults (Summer is a JSON-first web
                            // framework): pretty-printed output and explicit nulls keep field
                            // presence stable for clients. These intentionally differ from the
                            // compact NON_NULL defaults of SummerObjectMapper.
                            m.configure(SerializationFeature.INDENT_OUTPUT, false);
                            m.setSerializationInclusion(JsonInclude.Include.ALWAYS);

                            // Custom serializers only — SummerObjectMapper.create() already
                            // registers JavaTimeModule, so registering them on a fresh
                            // SimpleModule avoids double-registering it.
                            var customTimeSerializers = new SimpleModule();
                            customTimeSerializers.addSerializer(
                                    java.time.LocalDate.class,
                                    new LocalDateSerializer(DateTimeFormatter.ISO_DATE));
                            customTimeSerializers.addSerializer(
                                    java.time.LocalDateTime.class,
                                    new LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME));
                            customTimeSerializers.addSerializer(
                                    java.time.ZonedDateTime.class,
                                    new ZonedDateTimeSerializer(
                                            DateTimeFormatter.ISO_ZONED_DATE_TIME));
                            m.registerModule(customTimeSerializers);
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
