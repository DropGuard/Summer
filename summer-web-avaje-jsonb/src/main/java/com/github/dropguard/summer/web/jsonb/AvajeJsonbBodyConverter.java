package com.github.dropguard.summer.web.jsonb;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.BodyConverter;
import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * BodyConverter implementation backed by Avaje-JSONB.
 *
 * <p>Provides reflection-free, compile-time generated JSON serialization and deserialization with
 * direct streaming.
 */
@Internal
public class AvajeJsonbBodyConverter implements BodyConverter {

    private final Jsonb jsonb;

    public AvajeJsonbBodyConverter(Jsonb jsonb) {
        this.jsonb = Objects.requireNonNull(jsonb, "jsonb cannot be null");
    }

    public AvajeJsonbBodyConverter() {
        this(Jsonb.builder().build());
    }

    @Override
    public boolean supports(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    @Override
    public <T> T read(byte[] body, Class<T> type) throws IOException {
        if (body == null || body.length == 0) {
            return null;
        }
        JsonType<T> jsonType = jsonb.type(type);
        return jsonType.fromJson(body);
    }

    @Override
    public byte[] write(Object content) throws IOException {
        if (content == null) {
            return new byte[0];
        }
        @SuppressWarnings("unchecked")
        JsonType<Object> jsonType = (JsonType<Object>) jsonb.type(content.getClass());
        return jsonType.toJsonBytes(content);
    }

    @Override
    public void writeToStream(Object content, OutputStream out) throws IOException {
        if (content == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        JsonType<Object> jsonType = (JsonType<Object>) jsonb.type(content.getClass());
        jsonType.toJson(content, out);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    public Jsonb getJsonb() {
        return jsonb;
    }
}
