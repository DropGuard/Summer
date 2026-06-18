package test;

import summer.web.annotation.Get;
import summer.web.annotation.RestController;
import summer.web.PathUtils;

@RestController("/api/params")
class DummyController {
    @Get("/query-int")
    public void queryInt() {}
}

public class TestRegistrar {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = DummyController.class;
        System.out.println("Has RestController: " + clazz.isAnnotationPresent(RestController.class));
        for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
            Get get = m.getAnnotation(Get.class);
            if (get != null) {
                System.out.println("Get value via direct: " + get.value());
                String path = PathUtils.combinePaths(clazz.getAnnotation(RestController.class).value(), get.value());
                System.out.println("Combined path: " + path);
            }
        }
    }
}
