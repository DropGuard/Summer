package summer.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import summer.test.SummerTestProfile;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestProfile {
	Class<? extends SummerTestProfile> value();
}
