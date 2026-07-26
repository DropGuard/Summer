package com.github.dropguard.summer.data.redis.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.exception.DataSerializationException;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import io.lettuce.core.codec.RedisCodec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class JsonRedisCodec implements RedisCodec<String, Object> {

	private final ObjectMapper mapper;

	public JsonRedisCodec() {
		this(SummerObjectMapper.create());
	}

	public JsonRedisCodec(ObjectMapper mapper) {
		this.mapper = mapper;
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
