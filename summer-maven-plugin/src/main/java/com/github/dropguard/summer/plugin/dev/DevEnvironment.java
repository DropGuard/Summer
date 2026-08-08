package com.github.dropguard.summer.plugin.dev;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The dev app's rebuild + lifecycle: owns the compiler, the index rebuild, the child-JVM manager,
 * and the resource root. {@link TcpProxy} only forwards connections and runs the lazy-reload
 * barrier; on a dirty request it delegates the actual rebuild here.
 */
public final class DevEnvironment {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DevEnvironment.class);

    private final HotCompiler compiler;
    private final JandexFastIndexer indexer;
    private final AppProcessManager appManager;
    private final String mainClass;
    private final File resourcesDir;
    private volatile int backendPort;

    public DevEnvironment(
            HotCompiler compiler,
            JandexFastIndexer indexer,
            AppProcessManager appManager,
            String mainClass,
            File resourcesDir) {
        this.compiler = compiler;
        this.indexer = indexer;
        this.appManager = appManager;
        this.mainClass = mainClass;
        this.resourcesDir = resourcesDir;
    }

    /** The port the child JVM listens on (set by the last {@link #rebuild}). */
    public int backendPort() {
        return backendPort;
    }

    /**
     * Rebuilds from the changed files and restarts the child: java sources compile; resources
     * (yml/properties/static) copy into the output dir so the restarted child sees them on its
     * classpath. The child gets a fresh random port, which the proxy then forwards to.
     */
    public void rebuild(List<File> changed) throws Exception {
        List<File> sources = new ArrayList<>();
        for (File f : changed) {
            if (f.getName().endsWith(".java")) {
                sources.add(f);
            } else {
                copyResource(f);
            }
        }
        if (!sources.isEmpty()) {
            compiler.compile(sources);
            indexer.reindex(compiler.outputDir);
        }

        try (ServerSocket s = new ServerSocket(0)) {
            backendPort = s.getLocalPort();
        }
        appManager.start(backendPort, mainClass, compiler.classpath);
        // Wait a tiny bit for Netty to bind in the child JVM
        Thread.sleep(500);
    }

    private void copyResource(File changed) {
        if (resourcesDir == null) {
            log.warn("[Summer] Resource changed but no resources dir configured: " + changed);
            return;
        }
        Path root = resourcesDir.toPath();
        Path file = changed.toPath();
        if (!file.startsWith(root)) {
            log.warn("[Summer] Changed file outside resources dir, ignoring: " + changed);
            return;
        }
        try {
            Path relative = root.relativize(file);
            Path target = compiler.outputDir.toPath().resolve(relative);
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[Summer] Copied changed resource to " + target);
        } catch (Exception e) {
            log.error("[Summer] Failed to copy resource " + changed, e);
        }
    }
}
