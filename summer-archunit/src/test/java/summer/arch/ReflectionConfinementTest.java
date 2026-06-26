package summer.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ensures reflective API usage is confined to the {@code summer-runtime}
 * module.
 *
 * <p>
 * Two complementary rules enforce this:
 * </p>
 * <ul>
 * <li>{@code java.lang.reflect..} and {@code java.lang.invoke..} packages are
 * entirely off-limits outside the runtime module</li>
 * <li>{@code java.lang.Class} methods are filtered by a whitelist --only
 * metadata-inspection methods are allowed; loading / instantiating
 * ({@code forName}, {@code newInstance}) is blocked</li>
 * </ul>
 *
 * <p>
 * {@code DiEngine} is exempted because {@code DiEngine.create()} loads
 * AOT-generated classes via {@code Class.forName} — the single legitimate
 * reflective path outside {@code summer-runtime}.
 * </p>
 */
class ReflectionConfinementTest {

	private static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("summer..");
	}

	private static final Set<String> CLASS_BANNED = Set.of("getDeclaredMethods", "getMethod", "getMethods",
			"getDeclaredFields", "getField", "getFields", "getDeclaredConstructors", "getConstructor",
			"getConstructors", "getDeclaredClasses", "getEnclosingMethod", "getEnclosingConstructor", "forName",
			"newInstance");

	@Test
	@DisplayName("Reflection is confined to summer-runtime")
	void reflectionConfinedToRuntime() {

		DescribedPredicate<JavaMethodCall> callBannedClassMethods = new DescribedPredicate<>(
				"call banned java.lang.Class reflective methods") {
			@Override
			public boolean test(JavaMethodCall call) {

				return call.getTargetOwner().isEquivalentTo(Class.class) && CLASS_BANNED.contains(call.getName());
			}
		};

		// @formatter:off
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..summer.runtime..")
                .and().resideOutsideOfPackage("..summer.test..")
                .and().doNotHaveSimpleName("DiEngine")
                .should().dependOnClassesThat()
                    .resideInAnyPackage("java.lang.reflect..", "java.lang.invoke..")
                .orShould().callMethodWhere(callBannedClassMethods);
        // @formatter:on

		rule.check(classes);
	}
}
