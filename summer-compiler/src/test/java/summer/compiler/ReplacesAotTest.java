package summer.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

public class ReplacesAotTest {

	@Test
	void replacesCompilesSuccessfully() throws Exception {
		File dummyDir = new File("src/test/java/summer/compiler/dummy");

		Compilation compilation = javac().withProcessors(new SummerProcessor()).compile(
				JavaFileObjects.forResource(new File(dummyDir, "DummyInterface.java").toURI().toURL()),
				JavaFileObjects.forResource(new File(dummyDir, "DummyService.java").toURI().toURL()),
				JavaFileObjects.forResource(new File(dummyDir, "ReplacesOriginalConfig.java").toURI().toURL()),
				JavaFileObjects.forResource(new File(dummyDir, "ReplacesReplacementConfig.java").toURI().toURL()));

		assertThat(compilation).succeeded();

		// AOT context should be generated
		Optional<JavaFileObject> aotContext = compilation.generatedSourceFile("summer.core.aot.GeneratedAotContext");
		assertTrue(aotContext.isPresent(), "GeneratedAotContext should be generated");

		// Verify original config is excluded from generated context
		String contextSource = aotContext.get().getCharContent(true).toString();
		assertFalse(contextSource.contains("ReplacesOriginalConfig"),
				"Generated context should NOT reference the replaced config");
		assertTrue(contextSource.contains("ReplacesReplacementConfig"),
				"Generated context should reference the replacement config");
	}
}
