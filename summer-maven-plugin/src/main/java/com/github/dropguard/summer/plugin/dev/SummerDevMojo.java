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

            // 4. Init TCP Proxy (The core loop)
            TcpProxy proxy = new TcpProxy(port, compiler, indexer, appManager, mainClass);

            // 5. Init File Watcher (Triggers eager kill & dirty flag)
            DirectoryWatcher watcher =
                    new DirectoryWatcher(new File(project.getBasedir(), "src/main/java"));
            watcher.start(
                    changedFile -> {
                        appManager.kill(); // EAGER KILL: break the pipe
                        proxy.changedFiles.add(changedFile);
                        proxy.isDirty =
                                true; // LAZY COMPILE: proxy will pick this up on next request
                    });

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
            org.jboss.jandex.IndexReader reader = new org.jboss.jandex.IndexReader(fis);
            org.jboss.jandex.Index index = reader.read();
            for (org.jboss.jandex.ClassInfo clazz : index.getKnownClasses()) {
                for (org.jboss.jandex.MethodInfo method : clazz.methods()) {
                    // JVM Access Flags defined in JVMS 4.6
                    final int ACC_PUBLIC = 0x0001;
                    final int ACC_STATIC = 0x0008;

                    if (method.name().equals("main")
                            && (method.flags() & ACC_STATIC) != 0
                            && (method.flags() & ACC_PUBLIC) != 0) {
                        return clazz.name().toString();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read Jandex index for main class detection", e);
        }
        return null;
    }
}
