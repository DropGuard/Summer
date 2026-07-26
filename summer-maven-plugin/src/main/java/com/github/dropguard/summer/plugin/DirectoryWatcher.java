package com.github.dropguard.summer.plugin;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.maven.plugin.logging.Log;

/**
 * Recursive NIO file watcher with event debouncing.
 */
public class DirectoryWatcher {
	private final Log log;
	private final Path sourceDir;
	private final WatchService watcher;

	public DirectoryWatcher(Log log, File sourceDir) throws Exception {
		this.log = log;
		this.sourceDir = sourceDir.toPath();
		this.watcher = FileSystems.getDefault().newWatchService();
		registerAll(this.sourceDir);
	}

	private void registerAll(final Path start) throws Exception {
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
				dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public void start(java.util.function.Consumer<File> onFileChanged) {
		new Thread(() -> {
			try {
				while (true) {
					WatchKey key = watcher.take();
					boolean triggered = false;

					for (WatchEvent<?> event : key.pollEvents()) {
						Path context = (Path) event.context();
						if (context.toString().endsWith(".java")) {
							Path absolutePath = ((Path) key.watchable()).resolve(context);
							onFileChanged.accept(absolutePath.toFile());
							triggered = true;
						} else if (event.kind() == ENTRY_CREATE
								&& Files.isDirectory(((Path) key.watchable()).resolve(context))) {
							// Automatically watch newly created directories
							registerAll(((Path) key.watchable()).resolve(context));
						}
					}

					key.reset();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				log.error("File watcher died", e);
			}
		}, "Summer-DirectoryWatcher").start();
	}
}
