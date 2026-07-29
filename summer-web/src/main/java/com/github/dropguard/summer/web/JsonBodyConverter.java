mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.annotation.JsonInclude;
mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.databind.ObjectMapper;
mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.databind.SerializationFeature;
mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
mport com.github.dropguard.summer.core.Internal;
@Internal
import com.github.dropguard.summer.core.json.SummerObjectMapper;
mport com.github.dropguard.summer.core.Internal;
import java.io.IOException;
mport com.github.dropguard.summer.core.Internal;
import java.time.format.DateTimeFormatter;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Standard JSON implementation of BodyConverter using Jackson.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is a framework infrastructure bean provided by {@code WebInfrastructureConfiguration}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class JsonBodyConverter implements BodyConverter {
mport com.github.dropguard.summer.core.Internal;
    private static final ObjectMapper objectMapper;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    static {
mport com.github.dropguard.summer.core.Internal;
        objectMapper =
mport com.github.dropguard.summer.core.Internal;
                SummerObjectMapper.create(
mport com.github.dropguard.summer.core.Internal;
                        m -> {
mport com.github.dropguard.summer.core.Internal;
                            m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
mport com.github.dropguard.summer.core.Internal;
                            m.configure(SerializationFeature.INDENT_OUTPUT, true);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
                            var javaTimeModule =
mport com.github.dropguard.summer.core.Internal;
                                    new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
mport com.github.dropguard.summer.core.Internal;
                            javaTimeModule.addSerializer(
mport com.github.dropguard.summer.core.Internal;
                                    java.time.LocalDate.class,
mport com.github.dropguard.summer.core.Internal;
                                    new LocalDateSerializer(DateTimeFormatter.ISO_DATE));
mport com.github.dropguard.summer.core.Internal;
                            javaTimeModule.addSerializer(
mport com.github.dropguard.summer.core.Internal;
                                    java.time.LocalDateTime.class,
mport com.github.dropguard.summer.core.Internal;
                                    new LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME));
mport com.github.dropguard.summer.core.Internal;
                            javaTimeModule.addSerializer(
mport com.github.dropguard.summer.core.Internal;
                                    java.time.ZonedDateTime.class,
mport com.github.dropguard.summer.core.Internal;
                                    new ZonedDateTimeSerializer(
mport com.github.dropguard.summer.core.Internal;
                                            DateTimeFormatter.ISO_ZONED_DATE_TIME));
mport com.github.dropguard.summer.core.Internal;
                            m.registerModule(javaTimeModule);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
                            m.setSerializationInclusion(JsonInclude.Include.ALWAYS);
mport com.github.dropguard.summer.core.Internal;
                        });
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(String contentType) {
mport com.github.dropguard.summer.core.Internal;
        return contentType != null && contentType.toLowerCase().contains("application/json");
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public <T> T read(byte[] body, Class<T> type) throws IOException {
mport com.github.dropguard.summer.core.Internal;
        return objectMapper.readValue(body, type);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public byte[] write(Object content) throws IOException {
mport com.github.dropguard.summer.core.Internal;
        return objectMapper.writeValueAsBytes(content);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void writeToStream(Object content, java.io.OutputStream out) throws IOException {
mport com.github.dropguard.summer.core.Internal;
        objectMapper.writeValue(out, content);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String getContentType() {
mport com.github.dropguard.summer.core.Internal;
        return "application/json";
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
