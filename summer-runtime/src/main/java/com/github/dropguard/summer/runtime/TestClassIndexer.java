package com.github.dropguard.summer.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;

/**
 * Faithful port of Quarkus' {@code io.quarkus.test.common.TestClassIndexer}.
 *
 * <p>
 * In Quarkus the integration-test deployment re-indexes <em>exactly</em> the
 * {@code test-classes} directory of the running {@code @QuarkusTest} class and
 * hands that as an <em>additional archive</em> to the deployment — it never
 * reads a pre-baked {@code jandex-test.idx}, never bulk-scans every module's
 * test classes, and never needs an exclude-list. Bad (negative) fixtures live
 * in a <em>separate module</em> whose {@code test-classes} directory is simply
 * not on the path of the application's own {@code test-classes}, so they are
 * structurally unreachable from a whole-universe {@code @QuarkusTest}.
 * </p>
 *
 * <p>
 * This class reproduces that model for Summer: {@link #indexTestClasses(Class)}
 * indexes only the {@code test-classes} directory resolved from the given test
 * class's code source — nothing more. No {@code jandex-test.idx}, no full
 * classpath sweep, no allow/deny list. A negative fixture is reachable only
 * through the narrow {@code @SummerTest(classes=...)} path (which indexes the
 * named {@code .class} bytes directly via {@link NarrowIndexBuilder}), never
 * through the whole-universe path.
 * </p>
 */
public final class TestClassIndexer {

	private TestClassIndexer() {
	}

	/**
	 * Indexes the {@code test-classes} directory of the given test class, by
	 * walking its {@code .class} bytes with a {@link Indexer}. This is the exact
	 * Quarkus {@code TestClassIndexer.indexTestClasses(Class)} contract.
	 *
	 * @param testClass
	 *            a class on the test classpath (typically the {@code @SummerTest}
	 *            class)
	 * @return a freshly-built {@link Index} of that test-classes directory
	 */
	public static Index indexTestClasses(Class<?> testClass) {
		Path testClassesLocation = testClassesLocation(testClass);
		Indexer indexer = new Indexer();
		try {
			if (Files.isDirectory(testClassesLocation)) {
				indexTestClassesDir(indexer, testClassesLocation);
			} else {
				// jar / zip form: walk the root(s) of the archive.
				try (var fs = java.nio.file.FileSystems.newFileSystem(testClassesLocation)) {
					for (Path root : fs.getRootDirectories()) {
						indexTestClassesDir(indexer, root);
					}
				}
			}
		} catch (IOException e) {
			throw new java.io.UncheckedIOException("Unable to index the test-classes directory.", e);
		}
		return indexer.complete();
	}

	/**
	 * Resolves the {@code test-classes} directory for a test class: the directory
	 * that the class was loaded from. Mirrors Quarkus'
	 * {@code PathTestHelper.getTestClassesLocation} — the code source location of
	 * the class is its {@code test-classes} root.
	 */
	private static Path testClassesLocation(Class<?> testClass) {
		URL location = testClass.getProtectionDomain().getCodeSource().getLocation();
		try {
			Path p = Path.of(location.toURI());
			// A directory location for a test class IS the test-classes root.
			// A jar location is treated as a single archive to walk.
			return p;
		} catch (Exception e) {
			throw new IllegalStateException("Cannot resolve test-classes location for " + testClass, e);
		}
	}

	private static void indexTestClassesDir(Indexer indexer, Path testClassesLocation) throws IOException {
		if (!Files.exists(testClassesLocation)) {
			return;
		}
		Files.walkFileTree(testClassesLocation, new FileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (!file.getFileName().toString().endsWith(".class")) {
					return FileVisitResult.CONTINUE;
				}
				try (InputStream is = Files.newInputStream(file)) {
					indexer.index(is);
				} catch (Exception ignored) {
					// Skip classes we cannot read (e.g. corrupt / non-JVM bytes);
					// they are never DI beans.
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Infers the test class for a whole-universe container that was built without
	 * an explicit {@code @SummerTest} class (e.g. {@code Testing.build()}).
	 *
	 * <p>
	 * Quarkus' {@code @QuarkusTest} always has a test class, so its deployment
	 * always indexes exactly that class's {@code test-classes} directory. Summer
	 * keeps a parameterless {@code Testing.build()} for convenience; to stay
	 * faithful to the single-directory model we resolve the calling test class from
	 * the stack and index <em>its</em> {@code test-classes} directory — never a
	 * bulk sweep of every module's test classes. A negative fixture, which lives in
	 * a separate module, is simply not on that directory's path, so no exclude list
	 * is needed.
	 * </p>
	 *
	 * @return the inferred test class, or {@code null} if none can be resolved
	 */
	public static Class<?> inferTestClass() {
		for (StackTraceElement frame : new Throwable().getStackTrace()) {
			String cn = frame.getClassName();
			if (cn.startsWith("com.github.dropguard.summer.test.") || cn.startsWith("java.") || cn.startsWith("sun.")
					|| cn.startsWith("jdk.") || cn.startsWith("org.junit") || cn.startsWith("org.slf4j")
					|| cn.startsWith("io.grpc")) {
				continue;
			}
			try {
				Class<?> c = Class.forName(cn, false, TestClassIndexer.class.getClassLoader());
				if (c.getProtectionDomain().getCodeSource() == null) {
					continue;
				}
				URL loc = c.getProtectionDomain().getCodeSource().getLocation();
				String p = loc.getPath();
				if (p.contains("/test-classes/") || p.endsWith("/test-classes") || p.contains("\\test-classes\\")) {
					return c;
				}
			} catch (Exception | LinkageError ignored) {
				// Skip unresolvable frames; keep walking the stack.
			}
		}
		return null;
	}
}
