package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.fixtures.di.configprops.AppProperties;
import summer.test.annotation.SummerTest;

@SummerTest(engine = Engine.AOT, value = {AppProperties.class})
class AotTestGraphTest {

    final BeanContainer context;

    AotTestGraphTest(BeanContainer context) {
        this.context = context;
    }

    @Test
    void shouldLoadTestGraphWithOnlyEntryBean() {
        assertNotNull(context);
        assertEquals(Engine.AOT, context.engine());

        // AppProperties should be bound from application.yml
        AppProperties props = context.getBean(AppProperties.class);
        assertNotNull(props);
        assertEquals("summer-tck", props.name());
        assertEquals(8080, props.port());
        assertTrue(props.verbose());

        // Only AppProperties (+ AotDiMarker) should be present — no other beans
        assertTrue(context.componentTypes().size() <= 2,
                "TestGraph should contain minimal beans, got: " + context.componentTypes());
    }
}
