package com.github.dropguard.summer.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.apache.maven.plugin.logging.Log;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

/** Rapidly rebuilds the jandex.idx file directly from the output directory. */
public class JandexFastIndexer {

    private final Log log;

    public JandexFastIndexer(Log log) {
        this.log = log;
    }

    public void reindex(File outputDir) {
        try {
            log.info("[Summer] Rebuilding Jandex index...");
            Indexer indexer = new Indexer();
            indexDirectory(outputDir, outputDir, indexer);
            Index index = indexer.complete();

            File metaInf = new File(outputDir, "META-INF");
            if (!metaInf.exists()) metaInf.mkdirs();

            File idxFile = new File(metaInf, "jandex.idx");
            try (FileOutputStream out = new FileOutputStream(idxFile)) {
                IndexWriter writer = new IndexWriter(out);
                writer.write(index);
            }
            log.info("[Summer] Index updated.");
        } catch (Exception e) {
            log.error("Failed to rebuild Jandex index", e);
        }
    }

    private void indexDirectory(File baseDir, File currentDir, Indexer indexer) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                indexDirectory(baseDir, f, indexer);
            } else if (f.getName().endsWith(".class")) {
                try (InputStream stream = new FileInputStream(f)) {
                    indexer.index(stream);
                }
            }
        }
    }
}
