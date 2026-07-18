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
 * Ensures reflective API usage is confined to its legitimate owners.
 *
 * <p>
 * Two complementary rules enforce this:
 * </p>
 * <ul>
 * <li>{@code java.lang.reflect..} and {@code java.lang.invoke..} packages are
 * entirely off-limits outside the runtime/test/data-jdbc modules</li>
 * <li>{@code Class.forName} (class loading) is permitted <em>only</em> at the
 * few points where reflective loading is genuinely required, and is banned
 * everywhere else</li>
 * </ul>
 *
 * <p>
 * The allowed {@code Class.forName} sites are deliberate, not exceptions
 * granted to rot:
 * </p>
 * <ul>
 * <li>{@code DiEngine} (in {@code summer.core}) — the single engine loader; it
 * reflectively loads the compiled container/engine class. Both the production
 * path and the test-time AOT compiler funnel through it, so reflective loading
 * of generated classes lives in exactly one place.</li>
 * <li>{@code summer.runtime} — the runtime engine discovers and instantiates
 * beans via reflection; this is its defining mechanism.</li>
 * <li>{@code summer.test} — test infrastructure reflectively loads the optional
 * AOT engine and Mockito to break compile-time dependency cycles.</li>
 * <li>{@code summer.data.jdbc} — loads user-declared {@code @RowModel} record
 * classes so it can build their {@code RowMapper}s; loading domain models is a
 * data-module responsibility, not a cross-module reflection. The AOT path emits
 * these mappings at compile time (zero reflection); only the runtime engine
 * reflects here, symmetrically with how it loads {@code @Component}s.</li>
 * </ul>
 *
 * <p>
 * Anything outside these four sites — notably the AOT module, which must stay
 * reflection-free, and any other module — is forbidden from calling
 * {@code Class.forName}. This is what keeps the AOT path at (near) zero
 * reflection: a stray cross-module {@code forName} fails the build instead of
 * silently leaking.
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
                .and().resideOutsideOfPackage("..summer.data.jdbc..")
                .and().doNotHaveSimpleName("DiEngine")
                .should().dependOnClassesThat()
                    .resideInAnyPackage("java.lang.reflect..", "java.lang.invoke..")
                .orShould().callMethodWhere(callBannedClassMethods);
        // @formatter:on

		rule.check(classes);
	}

	/**
	 * {@code Class.forName} must not appear outside the four sites where reflective
	 * class loading is genuinely required (engine loader, runtime engine, test
	 * infrastructure, data-module model loading). Any other module — above all the
	 * AOT module, which is designed to stay reflection-free — is banned from
	 * loading classes by name.
	 */
	@Test
	@DisplayName("Class.forName is confined to its legitimate owners")
	void classLoadingConfined() {
		// @formatter:off
		ArchRule rule = noClasses()
				.that().resideOutsideOfPackage("..summer.runtime..")
				.and().resideOutsideOfPackage("..summer.test..")
				.and().resideOutsideOfPackage("..summer.data.jdbc..")
				.and().doNotHaveSimpleName("DiEngine")
				.should().callMethod(Class.class, "forName");
		// @formatter:on
		rule.check(classes);
	}
}
