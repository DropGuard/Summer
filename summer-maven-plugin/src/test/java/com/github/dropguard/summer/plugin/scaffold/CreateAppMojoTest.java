package com.github.dropguard.summer.plugin.scaffold;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code create-app} goal scaffolds a project from the summer-archetype templates (read off the
 * plugin classpath — single source of truth with the archetype, whose IT builds the same files).
 * This test asserts the scaffold shape: the build-parent pom, no AOT plugin declaration of its own
 * (summer-build-parent binds it), and the package-substituted sources.
 */
class CreateAppMojoTest {

    @TempDir Path tempDir;

    @Test
    void scaffoldsZeroDeclarationAotProject() throws Exception {
        CreateAppMojo mojo = new CreateAppMojo();
        setField(mojo, "groupId", "com.example");
        setField(mojo, "artifactId", "myapp");
        setField(mojo, "version", "1.0");
        setField(mojo, "packageName", "com.example");
        setField(mojo, "outputDirectory", tempDir.toFile());

        mojo.execute();

        Path project = tempDir.resolve("myapp");

        // The pom inherits the build-parent and declares no AOT plugin of its own — the parent's
        // <build><plugins> binds both the Jandex index and the generate-aot execution, so a
        // scaffolded app gets AOT with zero plugin declarations (the "zero-declaration" contract).
        String pom = Files.readString(project.resolve("pom.xml"));
        assertTrue(
                pom.contains("summer-build-parent"), "generated pom must inherit the build parent");
        assertFalse(
                pom.contains("<artifactId>summer-maven-plugin</artifactId>"),
                "generated pom must NOT declare the AOT plugin (summer-build-parent binds it)");
        assertFalse(
                pom.contains("<goal>generate-aot</goal>"),
                "the plugin execution must come from the parent, not the generated pom");

        // Java sources land under the package path with the package substituted.
        Path app = project.resolve("src/main/java/com/example/App.java");
        assertTrue(Files.exists(app), "App.java must be generated under the package path");
        assertTrue(
                Files.readString(app).contains("package com.example;"),
                "the package placeholder must be substituted");

        Path controller = project.resolve("src/main/java/com/example/HelloController.java");
        assertTrue(Files.exists(controller), "HelloController must be generated");
        assertTrue(
                Files.readString(project.resolve("src/main/resources/application.yml"))
                        .contains("engine: runtime"),
                "application.yml must default to the runtime engine");
    }

    @Test
    void refusesToOverwriteNonEmptyDirectory() throws Exception {
        Path existing = tempDir.resolve("taken");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("keep.txt"), "x");

        CreateAppMojo mojo = new CreateAppMojo();
        setField(mojo, "groupId", "com.example");
        setField(mojo, "artifactId", "taken");
        setField(mojo, "outputDirectory", tempDir.toFile());

        org.junit.jupiter.api.Assertions.assertThrows(
                org.apache.maven.plugin.MojoExecutionException.class, mojo::execute);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CreateAppMojo.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
