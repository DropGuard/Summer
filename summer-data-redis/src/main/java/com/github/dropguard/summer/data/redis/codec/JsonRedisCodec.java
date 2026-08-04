package com.github.dropguard.summer.data.redis.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.DataSerializationException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import io.lettuce.core.codec.RedisCodec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Redis codec that (de)serializes values as JSON using Jackson.
 *
 * <p>The codec is the <em>single</em> owner of the {@link ObjectMapper} used for Redis value
 * (de)serialization. Callers that also need to convert a decoded value into a concrete type (e.g.
 * {@code SummerRedisTemplate.get(key, Class)}) must obtain this same mapper via {@link #mapper()} —
 * never construct a second one. Two independently-created mappers would drift apart the moment
 * either is customized, silently breaking round-trips.
 *
 * <p>Null handling is explicit: a missing/absent value decodes to {@code null}. The codec never
 * stores a JSON {@code null} for a value — callers remove keys with {@code delete} instead.
 */
@Internal
public class JsonRedisCodec implements RedisCodec<String, Object> {

    private final ObjectMapper mapper;

    public JsonRedisCodec() {
        this(SummerObjectMapper.create());
    }

    public JsonRedisCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The single {@link ObjectMapper} this codec uses for (de)serialization. */
    public ObjectMapper mapper() {
        return mapper;
    }

    @Override
    public String decodeKey(ByteBuffer bytes) {
        byte[] array = new byte[bytes.remaining()];
        bytes.get(array);
        return new String(array, StandardCharsets.UTF_8);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        if (bytes == null || bytes.remaining() == 0) {
            return null;
        }
        byte[] array = new byte[bytes.remaining()];
        bytes.get(array);
        try {
            return mapper.readValue(array, Object.class);
        } catch (java.io.IOException e) {
            throw new DataSerializationException("Failed to decode Redis value to JSON", e);
        }
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ByteBuffer encodeValue(Object value) {
        try {
            byte[] jsonBytes = mapper.writeValueAsBytes(value);
            return ByteBuffer.wrap(jsonBytes);
        } catch (JsonProcessingException e) {
            throw new DataSerializationException("Failed to encode value to JSON", e);
        }
    }
}
