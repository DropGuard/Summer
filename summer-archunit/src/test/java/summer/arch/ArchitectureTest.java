package summer.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Architecture rules for Summer framework.
 *
 * <p>These rules enforce dependency direction and prevent architectural violations.</p>
 */
class ArchitectureTest {

	private static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter().importPackages("summer");
	}

	@Test
	@DisplayName("No circular dependencies between packages")
	void noCircularDependencies() {
		ArchRule rule = slices()
			.matching("summer.(*)")
			.should().beFreeOfCycles();
		rule.check(classes);
	}

	@Test
	@DisplayName("Plugin should not depend on compiler")
	void pluginShouldNotDependOnCompiler() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("summer.plugin")
			.should().dependOnClassesThat()
			.resideInAPackage("summer.compiler")
			.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("Compiler should not depend on plugin")
	void compilerShouldNotDependOnPlugin() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("summer.compiler")
			.should().dependOnClassesThat()
			.resideInAPackage("summer.plugin")
			.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("Core should not depend on web")
	void coreShouldNotDependOnWeb() {
		ArchRule rule = noClasses()
			.that().resideInAPackage("summer.core")
			.should().dependOnClassesThat()
			.resideInAPackage("summer.web")
			.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("Web should not depend on data persistence")
	void webShouldNotDependOnData() {
		ArchRule rule = noClasses()
			.that().resideInAnyPackage("summer.web", "summer.webmvc")
			.should().dependOnClassesThat()
			.resideInAnyPackage("summer.data.jdbc", "summer.data.redis")
			.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("Generator classes should not have static methods")
	void generatorClassesShouldNotHaveStaticMethods() {
		ArchRule rule = noMethods()
			.that().areDeclaredInClassesThat()
			.haveSimpleNameEndingWith("Generator")
			.should().beStatic()
			.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("No ClassGraph dependency")
	void noClassGraphDependency() {
		ArchRule rule = noClasses()
			.should().dependOnClassesThat()
			.resideInAnyPackage("io.github.classgraph..")
			.allowEmptyShould(true);
		rule.check(classes);
	}
}
