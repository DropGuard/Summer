package test;

import summer.runtime.RuntimeApplicationContext;
import summer.web.HttpContext;
import summer.web.Request;
import summer.web.HttpMethod;
import summer.web.http.RadixTreeHttpRouter;
import summer.web.ExceptionRegistry;
import summer.fixtures.web.dummy.ParameterTestController;
import summer.core.BeanContainer;

public class TestRouting {
    public static void main(String[] args) {
        try {
            BeanContainer ctx = RuntimeApplicationContext.builder()
                .registerComponent(ParameterTestController.class)
                .build();
            summer.web.HttpRouter.Builder builder = new summer.web.HttpRouter.Builder(RadixTreeHttpRouter::new);
            for (summer.web.RouteRegistrar registrar : ctx.getBeans(summer.web.RouteRegistrar.class)) {
                System.out.println("Found registrar: " + registrar.getClass().getName());
                registrar.registerControllers(builder, ctx);
            }
            summer.web.HttpRouter router = builder.build();
            System.out.println("Router built");

            Request req = new Request(HttpMethod.GET, "/api/params/query-int", "age=25", null, null);
            HttpContext httpCtx = new HttpContext(req);
            System.out.println("Routing...");
            router.route(httpCtx);
            byte[] bodyBytes = httpCtx.body();
            if (bodyBytes == null) {
                System.out.println("Body is NULL!");
            } else {
                System.out.println("Body: " + new String(bodyBytes));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
