package com.github.dropguard.summer.plugin.dev;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior of the recursive NIO watcher: .java events fire, others do not, new dirs are picked up.
 */
class DirectoryWatcherTest {

    @TempDir Path tempDir;

    @Test
    void firesOnJavaFileChangeButNotOnOthers() throws Exception {
        List<File> fired = new CopyOnWriteArrayList<>();
        DirectoryWatcher watcher = new DirectoryWatcher(tempDir.toFile());
        watcher.start(fired::add);

        Path javaFile = tempDir.resolve("App.java");
        Path txtFile = tempDir.resolve("notes.txt");
        Files.writeString(javaFile, "class App {}\n");
        Files.writeString(txtFile, "ignored");

        await(fired, f -> f.getName().endsWith(".java"), "the .java change must fire the callback");
        Thread.sleep(500);
        assertFalse(
                fired.stream().anyMatch(f -> f.getName().equals("notes.txt")),
                "a non-.java change must not fire");
    }

    @Test
    void autoRegistersNewSubdirectories() throws Exception {
        List<File> fired = new CopyOnWriteArrayList<>();
        DirectoryWatcher watcher = new DirectoryWatcher(tempDir.toFile());
        watcher.start(fired::add);

        Path sub = Files.createDirectories(tempDir.resolve("pkg"));
        // WatchService only queues events for REGISTERED paths: a file created before the
        // watcher processes the subdir's ENTRY_CREATE (and registers it) is dropped — an
        // inherent API race, not a watcher bug. So: create the dir, let it register, then file.
        Thread.sleep(500);
        Files.writeString(sub.resolve("Service.java"), "class Service {}\n");

        await(fired, f -> f.getName().equals("Service.java"), "a .java in a new subdir must fire");
    }

    private static void await(List<File> fired, Predicate<File> p, String message)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (fired.stream().anyMatch(p)) return;
            Thread.sleep(50);
        }
        fail("timed out: " + message + " (fired=" + fired + ")");
    }
}
