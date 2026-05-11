package summer.web;

import java.io.IOException;

/**
 * Interface for converting HTTP request bodies into Java objects and vice-versa.
 * Enables the framework to support multiple formats (JSON, Protobuf, XML, etc.)
 * based on the Content-Type header.
 */
public interface BodyConverter {

	/**
	 * Checks if this converter supports the given Content-Type.
	 */
	boolean supports(String contentType);

	/**
	 * Reads the raw byte body and deserializes it into an object of the given type.
	 */
	<T> T read(byte[] body, Class<T> type) throws IOException;

	/**
	 * Serializes an object into a byte array for the response.
	 */
	byte[] write(Object content) throws IOException;

	/**
	 * The default Content-Type this converter produces (e.g., "application/json").
	 */
	String getContentType();
}
