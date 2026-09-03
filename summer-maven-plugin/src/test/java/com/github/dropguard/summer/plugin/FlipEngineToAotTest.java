package com.github.dropguard.summer.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the byte-preserving contract of the packaged {@code application.yml} engine flip: only the
 * {@code engine} line may change — comments, key order and unrelated content must survive
 * byte-for-byte (the previous Jackson round-trip destroyed them).
 */
class FlipEngineToAotTest {

    @TempDir Path dir;

    private String flip(String original) throws Exception {
        Path yml = dir.resolve("application.yml");
        if (original != null) {
            Files.writeString(yml, original);
        }
        SummerMojo mojo =
                (SummerMojo)
                        Class.forName("com.github.dropguard.summer.plugin.SummerMojo")
                                .getDeclaredConstructor()
                                .newInstance();
        java.lang.reflect.Method m = SummerMojo.class.getDeclaredMethod("flipEngineToAot");
        // outputDirectory is a plugin @Parameter(defaultValue) field; set it reflectively.
        java.lang.reflect.Field out = SummerMojo.class.getDeclaredField("outputDirectory");
        out.setAccessible(true);
        out.set(mojo, dir.toFile());
        m.setAccessible(true);
        m.invoke(mojo);
        return Files.readString(yml);
    }

    @Test
    void noFileCreatesMinimalConfig() throws Exception {
        String result = flip(null);
        assertEquals(
                "# summer.engine set by summer-maven-plugin (AOT production build)"
                        + System.lineSeparator()
                        + "summer:"
                        + System.lineSeparator()
                        + "  engine: aot"
                        + System.lineSeparator(),
                result);
    }

    @Test
    void preservesCommentsAndKeyOrderWhenAppendingBlock() throws Exception {
        String original =
                """
                # my database settings — do not touch
                datasource:
                  url: jdbc:postgresql://localhost/app

                server:
                  port: 8080
                """;
        String result = flip(original);

        assertTrue(result.startsWith(original), "existing content byte-for-byte prefix");
        assertTrue(result.contains("summer:") && result.contains("engine: aot"));
        assertFalse(result.contains("datasource:\nsummer"), "appended at end, not interleaved");
    }

    @Test
    void insertsIntoExistingSummerBlockWithoutDisturbingSiblings() throws Exception {
        String original =
                """
                summer:
                  dev-mode: true
                other:
                  key: value
                """;
        String result = flip(original);

        String[] lines = result.split("\\R");
        int engineIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("engine: aot")) engineIdx = i;
        }
        assertTrue(engineIdx > 0, "engine line present");
        assertEquals(
                "dev-mode: true",
                lines[engineIdx - 1].trim(),
                "inserted inside summer block, after its last entry");
        assertEquals("other:", lines[engineIdx + 1].trim(), "sibling block untouched");
    }

    @Test
    void replacesValueOnlyAndKeepsInlineComment() throws Exception {
        String original = "summer:\n" + "  engine: runtime # flipped back by devs sometimes\n";
        String result = flip(original);

        assertTrue(
                result.contains("engine: aot # flipped back by devs sometimes"),
                "value swapped, indentation and trailing comment preserved");
        assertFalse(result.contains("runtime"));
    }

    @Test
    void idempotentSecondRunChangesNothingBeyondFirstRun() throws Exception {
        String once = flip("server:\n  port: 1\n");
        String twice = flip(once);
        assertEquals(once, twice, "re-running the goal must be a no-op");
    }

    @Test
    void tabIndentedExistingEngineLineIsMatchedAndUpdated() throws Exception {
        // Regression: the engine-line regex used Java string-escaped "\s" written as "\s" once
        // and a plain space char once ("\s"). The latter matched only the ASCII space literal, so
        // a tab-indented engine line was silently skipped — the engine flag stuck on the user's
        // previous value while the build claimed AOT ran. After fixing the regex (real "\s"
        // meta-char in both places), tab-indented lines match and the flip is correct.
        String original = "summer:\n\tengine: runtime\n";
        String result = flip(original);

        assertTrue(result.contains("engine: aot"), "tab-indented engine line must be updated");
        assertFalse(result.contains("runtime"));
    }

    @Test
    void tabIndentedNoEngineYetAppendsEngineLine() throws Exception {
        String original = "summer:\n\t# placeholder for future settings\n";
        String result = flip(original);

        assertTrue(
                result.contains("engine: aot"),
                "missing engine inside the summer block must be added");
    }
}
