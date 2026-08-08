package com.github.dropguard.summer.plugin.dev;

import java.io.File;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

// TEST resolution is DELIBERATE here (unlike generate-aot's COMPILE_PLUS_RUNTIME): dev mode
// runs the app against the TEST classpath so test-scoped dependencies are available while
// developing — same choice as Quarkus dev mode. Keep in sync with getTestClasspathElements().
@Mojo(name = "dev", requiresDependencyResolution = ResolutionScope.TEST)
@org.apache.maven.plugins.annotations.Execute(
        phase = org.apache.maven.plugins.annotations.LifecyclePhase.COMPILE)
public class SummerDevMojo extends AbstractMojo {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SummerDevMojo.class);

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "com.github.dropguard.summer.dev.port", defaultValue = "8080")
    private int port;

    @Parameter(property = "com.github.dropguard.summer.mainClass", required = false)
    private String mainClass;

    @Override
    public void execute() throws MojoExecutionException {
        log.info("[Summer] Starting Summer in Dev Mode...");

        try {
            // 1. Setup classpath and directories
            List<String> cpElements = project.getTestClasspathElements();
            String classpath = String.join(File.pathSeparator, cpElements);
            File outputDir = new File(project.getBuild().getOutputDirectory());
            if (mainClass == null) {
                mainClass = findMainClass(outputDir);
                if (mainClass == null) {
                    throw new MojoExecutionException(
                            "Could not auto-detect main class. Please specify"
                                    + " <com.github.dropguard.summer.mainClass> in pom.xml"
                                    + " properties.");
                }
                log.info("[Summer] Auto-detected Main Class: " + mainClass);
            }

            // 2. Init Compiler & Indexer
            HotCompiler compiler = new HotCompiler(classpath, outputDir);
            JandexFastIndexer indexer = new JandexFastIndexer();

            // 3. Init Process Manager
            AppProcessManager appManager = new AppProcessManager();

            // 4. Init the dev environment (rebuild + child lifecycle) and the TCP proxy. The
            //    resources root is passed so a change in src/main/resources (e.g. application.yml)
            //    reloads the child instead of silently doing nothing.
            File resourcesDir = new File(project.getBasedir(), "src/main/resources");
            DevEnvironment env =
                    new DevEnvironment(compiler, indexer, appManager, mainClass, resourcesDir);
            TcpProxy proxy = new TcpProxy(port, env);

            // 5. Init File Watchers (Triggers eager kill & dirty flag): java sources + resources.
            DirectoryWatcher watcher =
                    new DirectoryWatcher(new File(project.getBasedir(), "src/main/java"));
            DirectoryWatcher resourceWatcher = new DirectoryWatcher(resourcesDir, "");
            java.util.function.Consumer<java.io.File> onChange =
                    changedFile -> {
                        appManager.kill(); // EAGER KILL: break the pipe
                        proxy.changedFiles.add(changedFile);
                        proxy.isDirty =
                                true; // LAZY COMPILE: proxy will pick this up on next request
                    };
            watcher.start(onChange);
            resourceWatcher.start(onChange);

            // 6. Start the proxy and block the Maven thread
            proxy.start();

            // Keep the maven plugin alive
            Thread.currentThread().join();

        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to resolve classpath", e);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to start dev mode", e);
        }
    }

    private String findMainClass(File outputDir) {
        File idxFile = new File(outputDir, "META-INF/jandex.idx");
        if (!idxFile.exists()) return null;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(idxFile)) {
            org.jboss.jandex.Index index = new org.jboss.jandex.IndexReader(fis).read();
            java.util.List<String> mains =
                    index.getKnownClasses().stream()
                            .filter(
                                    clazz ->
                                            clazz.methods().stream()
                                                    .anyMatch(SummerDevMojo::isMain))
                            .map(clazz -> clazz.name().toString())
                            .toList();
            if (mains.size() > 1) {
                log.warn(
                        "[Summer] Multiple main classes detected ({}), using {}",
                        mains,
                        mains.get(0));
            }
            return mains.isEmpty() ? null : mains.get(0);
        } catch (Exception e) {
            log.warn("Failed to read Jandex index for main class detection", e);
            return null;
        }
    }

    /** The JVM entry contract: {@code public static void main(String[])}. */
    private static boolean isMain(org.jboss.jandex.MethodInfo method) {
        // JVMS 4.6 access flags — java.lang.reflect.Modifier is banned outside the runtime layer
        // (see ReflectionConfinementTest); the plugin must stay reflection-free.
        final short ACC_PUBLIC = 0x0001;
        final short ACC_STATIC = 0x0008;
        int flags = method.flags();
        if (!"main".equals(method.name())
                || (flags & ACC_PUBLIC) == 0
                || (flags & ACC_STATIC) == 0) {
            return false;
        }
        if (method.parametersCount() != 1) return false;
        org.jboss.jandex.Type paramType = method.parameters().get(0).type();
        if (!org.jboss.jandex.Type.Kind.ARRAY.equals(paramType.kind())) return false;
        boolean stringArray =
                "java.lang.String".equals(paramType.asArrayType().component().name().toString());
        return stringArray && org.jboss.jandex.Type.create(void.class).equals(method.returnType());
    }
}
