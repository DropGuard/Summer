package summer.web.server;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import summer.web.Request;

public class HttpRequestParserTest {

    @Test
    public void testParseTextRequest() throws Exception {
        String rawRequest = "POST /test HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 11\r\n" +
                "\r\n" +
                "Hello World";
        
        InputStream input = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));
        Request request = HttpRequestParser.parse(input);
        
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/test", request.getPath());
        assertEquals("text/plain", request.getContentType());
        assertEquals("Hello World", new String(request.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void testParseBinaryRequest() throws Exception {
        byte[] binaryBody = new byte[] { 0x00, 0x01, 0x02, 0x03, (byte)0xFF, (byte)0xFE, 0x0D, 0x0A, 0x00 };
        String headers = "POST /binary HTTP/1.1\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: " + binaryBody.length + "\r\n" +
                "\r\n";
        
        byte[] headerBytes = headers.getBytes(StandardCharsets.UTF_8);
        byte[] fullRequest = new byte[headerBytes.length + binaryBody.length];
        System.arraycopy(headerBytes, 0, fullRequest, 0, headerBytes.length);
        System.arraycopy(binaryBody, 0, fullRequest, headerBytes.length, binaryBody.length);
        
        InputStream input = new ByteArrayInputStream(fullRequest);
        Request request = HttpRequestParser.parse(input);
        
        assertNotNull(request);
        assertEquals("/binary", request.getPath());
        assertArrayEquals(binaryBody, request.getBody());
    }

    @Test
    public void testParseKeepAliveRequest() throws Exception {
        String firstRequest = "GET /first HTTP/1.1\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "12345";
        String secondRequest = "GET /second HTTP/1.1\r\n" +
                "Content-Length: 3\r\n" +
                "\r\n" +
                "ABC";
        
        byte[] fullStream = (firstRequest + secondRequest).getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(fullStream);
        
        Request req1 = HttpRequestParser.parse(input);
        assertNotNull(req1);
        assertEquals("/first", req1.getPath());
        assertEquals("12345", new String(req1.getBody(), StandardCharsets.UTF_8));
        
        Request req2 = HttpRequestParser.parse(input);
        assertNotNull(req2);
        assertEquals("/second", req2.getPath());
        assertEquals("ABC", new String(req2.getBody(), StandardCharsets.UTF_8));
    }
}
