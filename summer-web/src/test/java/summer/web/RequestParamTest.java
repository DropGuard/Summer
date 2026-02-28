package summer.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RequestParamTest {

    @Test
    public void testRequestParamAnnotation() {
        // 创建测试请求，包含查询参数
        Request request = new Request(
                "GET",
                "/api/users",
                "name=john&age=30&active=true",
                new byte[0]);

        // 测试获取单个查询参数
        String name = request.getQueryParameter("name");
        assertEquals("john", name);

        String age = request.getQueryParameter("age");
        assertEquals("30", age);

        String active = request.getQueryParameter("active");
        assertEquals("true", active);

        // 测试获取不存在的参数
        String nonexistent = request.getQueryParameter("nonexistent");
        assertNull(nonexistent);
    }

    @Test
    public void testRequestParamParsing() {
        // 创建测试请求，包含查询参数
        Request request = new Request(
                "GET",
                "/api/search",
                "q=test+query&page=1&limit=10&sort=asc",
                new byte[0]);

        // 测试获取所有查询参数
        var params = request.getQueryParameters();
        assertEquals(4, params.size());
        assertEquals("test query", params.get("q"));
        assertEquals("1", params.get("page"));
        assertEquals("10", params.get("limit"));
        assertEquals("asc", params.get("sort"));
    }

    @Test
    public void testRequestParamWithSpecialCharacters() {
        // 创建测试请求，包含特殊字符的查询参数
        Request request = new Request(
                "GET",
                "/api/items",
                "filter=%26%3D%2B%2F%3F%23%25",
                new byte[0]);

        // 测试解码特殊字符
        String filter = request.getQueryParameter("filter");
        assertEquals("&=+/?#%", filter);
    }
}
