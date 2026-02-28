package summer.web;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.annotation.JsonInclude;
import summer.validation.ValidationResult;

/**
 * Represents an HTTP response.
 */
public class Response {
    private final OutputStream output;
    private final Map<String, String> headers = new HashMap<>();
    private int statusCode = 200;

    public Response(OutputStream output) {
        this.output = output;
    }
    
    public OutputStream getOutputStream() {
        return output;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public void ok(String content) {
        send(200, content, "text/plain");
    }

    public void ok(Object content) {
        sendJson(200, content);
    }

    public void created(String location) {
        setHeader("Location", location);
        send(201, null, null);
    }

    public void created(String location, Object content) {
        setHeader("Location", location);
        sendJson(201, content);
    }

    public void notFound() {
        send(404, "Not Found", "text/plain");
    }

    public void badRequest(String message) {
        send(400, message, "text/plain");
    }
    
    public void validationError(ValidationResult validationResult) {
        // Create a JSON response with validation errors
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Validation Failed");
        errorResponse.put("status", 400);
        errorResponse.put("details", validationResult.getErrors());
        
        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            send(400, json, "application/json");
        } catch (JsonProcessingException e) {
            send(400, "Validation failed", "text/plain");
        }
    }

    public void error(String message) {
        send(500, message, "text/plain");
    }

    public void error(Exception e) {
        send(500, e.getMessage(), "text/plain");
    }

    public void json(Object content) {
        sendJson(200, content);
    }

    public void send(int statusCode, String content, String contentType) {
        this.statusCode = statusCode;
        
        try {
            if (contentType != null) {
                setHeader("Content-Type", contentType);
            }
            
            String statusLine = "HTTP/1.1 " + statusCode + " " + getStatusCodeText(statusCode);
            StringBuilder headerLines = new StringBuilder();
            
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerLines.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
            
            String response = statusLine + "\r\n" + headerLines.toString() + "\r\n" + 
                           (content != null ? content : "");
            
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ObjectMapper objectMapper;
    
    static {
        objectMapper = new ObjectMapper();
        // 配置 ObjectMapper
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 支持 Java 8 时间类型
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(java.time.LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ISO_DATE));
        javaTimeModule.addSerializer(java.time.LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME));
        javaTimeModule.addSerializer(java.time.ZonedDateTime.class, new ZonedDateTimeSerializer(DateTimeFormatter.ISO_ZONED_DATE_TIME));
        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略 null 值的字段
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    private void sendJson(int statusCode, Object content) {
        try {
            String json = objectMapper.writeValueAsString(content);
            send(statusCode, json, "application/json");
        } catch (JsonProcessingException e) {
            send(500, "Error converting to JSON: " + e.getMessage(), "text/plain");
        }
    }

    private String getStatusCodeText(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}