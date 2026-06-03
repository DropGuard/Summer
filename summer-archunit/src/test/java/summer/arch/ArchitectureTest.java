package summer.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for Summer framework.
 *
 * <p>
 * These rules enforce the layered architecture and core design principles:
 * </p>
 * <ul>
 * <li>Layered dependency direction</li>
 * <li>No circular dependencies</li>
 * <li>No ClassGraph / CGLIB / ByteBuddy dependencies</li>
 * </ul>
 *
 */
class ArchitectureTest {

	private static final String[] PACKAGES = {
			"summer.core", "summer.web", "summer.aop", "summer.tx",
			"summer.runtime", "summer.compiler", "summer.data", "summer.boot",
			"summer.web.netty", "summer.grpc", "summer.validation", "summer.test",
			"summer.tck", "summer.arch"
	};

	private static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter().importPackages(PACKAGES);
	}

	// --- Layered Architecture ---

	@Test
	@DisplayName("Layered architecture: dependencies flow downward only")
	void layeredArchitecture() {
		// @formatter:off
		Architectures.LayeredArchitecture rule = Architectures.layeredArchitecture()
				.consideringOnlyDependenciesInAnyPackage("..summer..")
				.ignoreDependency("summer.example..", "..")
				.ignoreDependency("summer.realworld..", "..")
				.ignoreDependency("summer.benchmark..", "..")
				.ignoreDependency("..", "summer.example..")
				.ignoreDependency("..", "summer.realworld..")
				.ignoreDependency("..", "summer.benchmark..")

				// layer definitions
				.layer("Core")         .definedBy("..summer.core..")
				.layer("Infrastructure").definedBy("..summer.runtime..", "..summer.compiler..")
				.layer("Web")          .definedBy("..summer.web..", "..summer.boot..")
				.layer("Data")         .definedBy("..summer.data..")
				.layer("CrossCutting") .definedBy("..summer.aop..", "..summer.tx..", "..summer.validation..")
				.layer("Server")       .definedBy("..summer.web.netty..", "..summer.grpc..")
				.layer("Test")         .definedBy("..summer.test..", "..summer.tck..", "..summer.arch..")

				// access constraints
				.whereLayer("Core")         .mayNotAccessAnyLayer()
				.whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Web", "Data", "CrossCutting", "Server", "Test")
				.whereLayer("Web")          .mayOnlyBeAccessedByLayers("Infrastructure", "Server", "Test")
				.whereLayer("Data")         .mayOnlyBeAccessedByLayers("Infrastructure", "Test")
				.whereLayer("CrossCutting") .mayOnlyBeAccessedByLayers("Web", "Data", "Infrastructure", "Server", "Test")
				.whereLayer("Server")       .mayOnlyBeAccessedByLayers("Test")
				.whereLayer("Test")         .mayOnlyAccessLayers("Core", "Infrastructure", "Web", "Data", "CrossCutting", "Server");
		// @formatter:on
		rule.check(classes);
	}

	// --- Core Design Principles ---

	@Test
	@DisplayName("No circular dependencies between packages")
	void noCircularDependencies() {
		ArchRule rule = com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices().matching("summer.(*)")
				.should().beFreeOfCycles()
				.ignoreDependency("summer.example..", "..")
				.ignoreDependency("summer.realworld..", "..")
				.ignoreDependency("summer.benchmark..", "..")
				.ignoreDependency("..", "summer.example..")
				.ignoreDependency("..", "summer.realworld..")
				.ignoreDependency("..", "summer.benchmark..");
		rule.check(classes);
	}

	@Test
	@DisplayName("No ClassGraph dependency")
	void noClassGraphDependency() {
		ArchRule rule = noClasses().should().dependOnClassesThat().resideInAnyPackage("io.github.classgraph..")
				.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("No CGLIB dependency")
	void noCglibDependency() {
		ArchRule rule = noClasses().should().dependOnClassesThat()
				.resideInAnyPackage("net.sf.cglib..", "org.springframework.cglib..").allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	@DisplayName("No ByteBuddy dependency")
	void noByteBuddyDependency() {
		ArchRule rule = noClasses().should().dependOnClassesThat()
				.resideInAnyPackage("net.bytebuddy..").allowEmptyShould(true);
		rule.check(classes);
	}

}
