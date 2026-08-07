package com.github.dropguard.summer.plugin.scaffold;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scaffolds a new Summer application from the {@code summer-archetype} templates — the same files
 * the {@code archetype:generate} flow produces, generated in-process so the UX is a single command
 * with no archetype catalog prompts:
 *
 * <pre>
 * mvn com.github.dropguard:summer-maven-plugin:create-app -DartifactId=myapp
 * </pre>
 *
 * <p>The templates are read from the {@code summer-archetype} artifact on the plugin classpath
 * (single source of truth — the archetype IT builds and verifies the very same files). The
 * generated project inherits {@code summer-build-parent} and declares the AOT plugin bare, so it
 * builds AOT out of the box.
 */
@Mojo(name = "create-app", requiresProject = false, threadSafe = true)
public class CreateAppMojo extends AbstractMojo {

    private static final Logger log = LoggerFactory.getLogger(CreateAppMojo.class);

    private static final String TEMPLATES_PREFIX = "archetype-resources/";

    @Parameter(property = "groupId", defaultValue = "com.example")
    private String groupId;

    @Parameter(property = "artifactId", required = true)
    private String artifactId;

    @Parameter(property = "version", defaultValue = "1.0")
    private String version;

    @Parameter(property = "package")
    private String packageName;

    @Parameter(property = "outputDirectory", defaultValue = "${user.dir}")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        String pkg = packageName == null || packageName.isBlank() ? groupId : packageName;
        File projectDir = new File(outputDirectory, artifactId);
        if (projectDir.exists() && projectDir.list().length > 0) {
            throw new MojoExecutionException(
                    "Target directory already exists and is not empty: " + projectDir);
        }

        // LinkedHashMap is banned by the framework's ArchUnit rules; substitution order does
        // not matter here (no placeholder overlaps in the templates).
        Map<String, String> properties = new HashMap<>();
        properties.put("groupId", groupId);
        properties.put("artifactId", artifactId);
        properties.put("version", version);
        properties.put("package", pkg);
        properties.put("frameworkVersion", frameworkVersion());

        try {
            expand(projectDir.toPath(), pkg, properties);
        } catch (IOException e) {
            throw new MojoExecutionException("[Summer] create-app failed: " + e.getMessage(), e);
        }
        log.info(
                "[Summer] Created Summer application at {} (groupId={}, artifactId={})",
                projectDir,
                groupId,
                artifactId);
    }

    /** The framework version: the summer-archetype artifact's own version on this classpath. */
    private String frameworkVersion() throws MojoExecutionException {
        try {
            Enumeration<URL> resources =
                    getClass()
                            .getClassLoader()
                            .getResources(
                                    "META-INF/maven/com.github.dropguard/summer-archetype/pom.properties");
            if (resources.hasMoreElements()) {
                try (InputStream in = resources.nextElement().openStream()) {
                    var props = new java.util.Properties();
                    props.load(in);
                    return props.getProperty("version", "0.1.0");
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "[Summer] create-app: cannot read summer-archetype version", e);
        }
        return "0.1.0";
    }

    /**
     * Walks every template under archetype-resources/ on the classpath, substitutes, and writes.
     */
    private void expand(Path projectDir, String pkg, Map<String, String> properties)
            throws IOException, MojoExecutionException {
        Enumeration<URL> roots = getClass().getClassLoader().getResources(TEMPLATES_PREFIX);
        boolean any = false;
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            if ("jar".equals(url.getProtocol())) {
                any |= expandJar(url, projectDir, pkg, properties);
            } else {
                any |= expandDirectory(url, projectDir, pkg, properties);
            }
        }
        if (!any) {
            throw new MojoExecutionException(
                    "[Summer] create-app: summer-archetype templates not found on the plugin"
                            + " classpath");
        }
    }

    private boolean expandJar(URL url, Path projectDir, String pkg, Map<String, String> properties)
            throws IOException {
        String path = url.getPath();
        String jarPath = path.substring(0, path.indexOf('!'));
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring("file:".length());
        }
        String prefix =
                URLDecoder.decode(path.substring(path.indexOf('!') + 2), StandardCharsets.UTF_8);
        boolean any = false;
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || entry.isDirectory()) continue;
                String relative = name.substring(prefix.length());
                if (relative.isEmpty()) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    writeTemplate(in, relative, projectDir, pkg, properties);
                }
                any = true;
            }
        }
        return any;
    }

    private boolean expandDirectory(
            URL url, Path projectDir, String pkg, Map<String, String> properties)
            throws IOException {
        File dir;
        try {
            dir = new File(url.toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException("invalid template URL: " + url, e);
        }
        boolean any = false;
        java.util.List<File> files = new java.util.ArrayList<>();
        collect(dir, files);
        for (File file : files) {
            String relative = file.getAbsolutePath().substring(dir.getAbsolutePath().length() + 1);
            try (InputStream in = Files.newInputStream(file.toPath())) {
                writeTemplate(in, relative, projectDir, pkg, properties);
            }
            any = true;
        }
        return any;
    }

    private void collect(File dir, java.util.List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, result);
            else result.add(child);
        }
    }

    private void writeTemplate(
            InputStream in,
            String relative,
            Path projectDir,
            String pkg,
            Map<String, String> properties)
            throws IOException {
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        for (Map.Entry<String, String> e : properties.entrySet()) {
            content = content.replace("${" + e.getKey() + "}", e.getValue());
        }
        // packaged=true semantics: Java sources land under the package path.
        if (relative.endsWith(".java") && relative.startsWith("src/main/java/")) {
            relative =
                    "src/main/java/"
                            + pkg.replace('.', '/')
                            + "/"
                            + relative.substring("src/main/java/".length());
        }
        Path target = projectDir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
