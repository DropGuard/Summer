package summer.aot;

import java.io.File;
import org.jboss.jandex.IndexView;

/**
 * Shared context for the AOT build pipeline. Holds the Jandex index and output
 * directory — the two runtime-only values that generators need but cannot be
 * discovered via {@code @Component}.
 *
 * @param index
 *            the composite Jandex index for the project
 * @param outputDir
 *            directory to write generated {@code .java} files
 */
public record BuildContext(IndexView index, File outputDir) {
}
