mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.redis.codec;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.core.JsonProcessingException;
mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.databind.ObjectMapper;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.DataSerializationException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
mport com.github.dropguard.summer.core.Internal;
import io.lettuce.core.codec.RedisCodec;
mport com.github.dropguard.summer.core.Internal;
import java.nio.ByteBuffer;
mport com.github.dropguard.summer.core.Internal;
import java.nio.charset.StandardCharsets;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
@Internal
mport com.github.dropguard.summer.core.Internal;
 * Redis codec that (de)serializes values as JSON using Jackson.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>The codec is the <em>single</em> owner of the {@link ObjectMapper} used for Redis value
mport com.github.dropguard.summer.core.Internal;
 * (de)serialization. Callers that also need to convert a decoded value into a concrete type (e.g.
mport com.github.dropguard.summer.core.Internal;
 * {@code SummerRedisTemplate.get(key, Class)}) must obtain this same mapper via {@link #mapper()} —
mport com.github.dropguard.summer.core.Internal;
 * never construct a second one. Two independently-created mappers would drift apart the moment
mport com.github.dropguard.summer.core.Internal;
 * either is customized, silently breaking round-trips.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Null handling is explicit: a missing/absent value decodes to {@code null}. The codec never
mport com.github.dropguard.summer.core.Internal;
 * stores a JSON {@code null} for a value — callers remove keys with {@code delete} instead.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class JsonRedisCodec implements RedisCodec<String, Object> {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final ObjectMapper mapper;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public JsonRedisCodec() {
mport com.github.dropguard.summer.core.Internal;
        this(SummerObjectMapper.create());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public JsonRedisCodec(ObjectMapper mapper) {
mport com.github.dropguard.summer.core.Internal;
        this.mapper = mapper;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** The single {@link ObjectMapper} this codec uses for (de)serialization. */
mport com.github.dropguard.summer.core.Internal;
    public ObjectMapper mapper() {
mport com.github.dropguard.summer.core.Internal;
        return mapper;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String decodeKey(ByteBuffer bytes) {
mport com.github.dropguard.summer.core.Internal;
        byte[] array = new byte[bytes.remaining()];
mport com.github.dropguard.summer.core.Internal;
        bytes.get(array);
mport com.github.dropguard.summer.core.Internal;
        return new String(array, StandardCharsets.UTF_8);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object decodeValue(ByteBuffer bytes) {
mport com.github.dropguard.summer.core.Internal;
        if (bytes == null || bytes.remaining() == 0) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        byte[] array = new byte[bytes.remaining()];
mport com.github.dropguard.summer.core.Internal;
        bytes.get(array);
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            return mapper.readValue(array, Object.class);
mport com.github.dropguard.summer.core.Internal;
        } catch (java.io.IOException e) {
mport com.github.dropguard.summer.core.Internal;
            throw new DataSerializationException("Failed to decode Redis value to JSON", e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public ByteBuffer encodeKey(String key) {
mport com.github.dropguard.summer.core.Internal;
        return ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public ByteBuffer encodeValue(Object value) {
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            byte[] jsonBytes = mapper.writeValueAsBytes(value);
mport com.github.dropguard.summer.core.Internal;
            return ByteBuffer.wrap(jsonBytes);
mport com.github.dropguard.summer.core.Internal;
        } catch (JsonProcessingException e) {
mport com.github.dropguard.summer.core.Internal;
            throw new DataSerializationException("Failed to encode value to JSON", e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
