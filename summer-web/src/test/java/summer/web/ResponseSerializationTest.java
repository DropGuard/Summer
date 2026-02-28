package summer.web;

import org.junit.jupiter.api.Test;
import summer.web.Response;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseSerializationTest {

    @Test
    public void testJsonSerializationWithJavaTimeTypes() {
        // 创建一个包含 Java 8 时间类型的复杂对象
        Map<String, Object> testData = new HashMap<>();
        testData.put("stringValue", "Hello World");
        testData.put("intValue", 42);
        testData.put("booleanValue", true);
        testData.put("localDate", LocalDate.now());
        testData.put("localDateTime", LocalDateTime.now());
        testData.put("zonedDateTime", ZonedDateTime.now());
        
        // 创建响应对象
        Response response = new Response(System.out);
        
        // 尝试序列化
        try {
            // 这里我们实际上不会发送响应到输出流，
            // 但可以通过捕获输出或使用 mock 对象来测试
            response.json(testData);
            
            // 如果没有抛出异常，我们就认为成功
            assertTrue(true);
        } catch (Exception e) {
            fail("序列化过程中抛出了异常: " + e.getMessage());
        }
    }
    
    @Test
    public void testJsonSerializationWithCollections() {
        // 创建包含集合类型的对象
        List<Map<String, Object>> listData = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Item 1");
        item1.put("value", 10);
        listData.add(item1);
        
        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Item 2");
        item2.put("value", 20);
        listData.add(item2);
        
        Response response = new Response(System.out);
        
        try {
            response.json(listData);
            assertTrue(true);
        } catch (Exception e) {
            fail("集合类型序列化失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testJsonSerializationWithNullValues() {
        // 创建包含 null 值的对象
        Map<String, Object> testData = new HashMap<>();
        testData.put("nullValue", null);
        testData.put("validValue", "Some Value");
        
        Response response = new Response(System.out);
        
        try {
            response.json(testData);
            assertTrue(true);
        } catch (Exception e) {
            fail("包含 null 值的对象序列化失败: " + e.getMessage());
        }
    }
}
