package com.github.dropguard.summer.plugin.dev;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Recursive NIO file watcher with event debouncing. */
public class DirectoryWatcher {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DirectoryWatcher.class);
    private final Path sourceDir;
    private final String suffix; // files not ending in this suffix are ignored; "" = all files
    private final WatchService watcher;
    private volatile Thread thread;

    public DirectoryWatcher(File sourceDir) throws Exception {
        this(sourceDir, ".java");
    }

    public DirectoryWatcher(File sourceDir, String suffix) throws Exception {
        this.sourceDir = sourceDir.toPath();
        this.suffix = suffix;
        this.watcher = FileSystems.getDefault().newWatchService();
        registerAll(this.sourceDir);
    }

    private void registerAll(final Path start) throws Exception {
        Files.walkFileTree(
                start,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws java.io.IOException {
                        dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    public void start(java.util.function.Consumer<File> onFileChanged) {
        if (thread != null) {
            throw new IllegalStateException("DirectoryWatcher already started");
        }
        Thread watcherThread =
                new Thread(() -> watchLoop(onFileChanged), "Summer-DirectoryWatcher");
        watcherThread.setDaemon(true);
        thread = watcherThread;
        watcherThread.start();
    }

    /** Stops the watcher loop. Safe to call from a shutdown hook or twice. */
    public void stop() {
        Thread watcherThread = thread;
        if (watcherThread == null) {
            return;
        }
        watcherThread.interrupt();
        try {
            watcher.close();
        } catch (IOException e) {
            log.warn("[Summer] Failed to close watch service", e);
        }
    }

    private void watchLoop(java.util.function.Consumer<File> onFileChanged) {
        while (true) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return; // stop() called — clean exit
            }
            try {
                boolean overflow = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == OVERFLOW) {
                        // The OS dropped events (e.g. an IDE touched hundreds of files). The
                        // context is null by contract; recovering means rescanning everything.
                        overflow = true;
                        continue;
                    }
                    Path context = (Path) event.context();
                    if (context == null) {
                        continue;
                    }
                    if (suffix.isEmpty() || context.toString().endsWith(suffix)) {
                        onFileChanged.accept(((Path) key.watchable()).resolve(context).toFile());
                    } else if (event.kind() == ENTRY_CREATE
                            && Files.isDirectory(((Path) key.watchable()).resolve(context))) {
                        // Automatically watch newly created directories
                        registerAll(((Path) key.watchable()).resolve(context));
                    }
                }
                if (overflow) {
                    resync(onFileChanged);
                }
            } catch (Exception e) {
                // One bad event (or one throwing consumer) must not kill hot reload.
                log.warn("[Summer] File watcher event processing failed; continuing", e);
            } finally {
                key.reset();
            }
        }
    }

    /**
     * Recovers from a WatchService OVERFLOW: the dropped events are unknowable, so every tracked
     * file is re-emitted as changed. The lazy-reload barrier turns that into a full recompile — the
     * same cost as an initial boot, paid only when events were actually lost.
     */
    private void resync(java.util.function.Consumer<File> onFileChanged) {
        log.warn("[Summer] Watch service dropped events (OVERFLOW) — rescanning {}", sourceDir);
        try {
            Files.walkFileTree(
                    sourceDir,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (suffix.isEmpty() || file.toString().endsWith(suffix)) {
                                onFileChanged.accept(file.toFile());
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (Exception e) {
            log.error("[Summer] Rescan after watch-event overflow failed", e);
        }
    }
}
