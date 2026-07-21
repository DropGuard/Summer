package summer.core.bean;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.IndexView;

/**
 * Associates classes with their originating archive and retains each archive's
 * own {@link IndexView} separately — the index is <b>not</b> collapsed into a
 * single merged view. Bean discovery reads the indexes directly, and the set of
 * "known classes" depends entirely on which index was loaded:
 * {@link JandexIndexLoader#applicationIndex()} knows only production classes,
 * while {@link JandexIndexLoader#testIndex()} also knows test-class beans.
 *
 * <p>
 * An <b>archive</b> is the immutable unit of bean discovery: one jar (or one
 * build output directory) is one archive, named after the artifact it ships in.
 * Archives are isolated for {@code @ConditionalOnBean} visibility — a bean's
 * condition is satisfied only by another bean in the <em>same</em> archive (see
 * {@link #archiveOf(String)}). Bean <em>injection</em> remains global; only the
 * condition-evaluation boundary is archive-scoped.
 * </p>
 */
public final class ModuleIndex {

	private final IndexView mergedIndex;
	private final Map<String, String> classToArchive;
	private final Set<String> allTypeNames;
	private final Map<String, IndexView> archiveIndexes;

	/**
	 * @param mergedIndex
	 *            fallback merged view for cross-archive annotation resolution
	 * @param classToArchive
	 *            class name → archive name
	 * @param archiveIndexes
	 *            archive name → its raw IndexView (not merged)
	 */
	public ModuleIndex(IndexView mergedIndex, Map<String, String> classToArchive,
			Map<String, IndexView> archiveIndexes) {
		this.mergedIndex = mergedIndex;
		this.classToArchive = classToArchive;
		this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
		this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
	}

	/**
	 * Wraps a single merged {@link IndexView} as a one-archive {@link ModuleIndex}.
	 * Used when a caller has only the merged index (no per-archive split) —
	 * discovery iterates the whole index as one archive. Also the bridge for legacy
	 * callers (e.g. the production AOT build) that supply an {@code IndexView}
	 * directly; with a single archive, {@code @ConditionalOnBean} visibility is
	 * effectively global.
	 */
	public static ModuleIndex single(IndexView mergedIndex) {
		Map<String, String> classToArchive = new java.util.HashMap<>();
		Map<String, IndexView> archiveIndexes = new java.util.HashMap<>();
		for (String typeName : mergedIndex.getKnownClasses().stream().map(Object::toString).toList()) {
			classToArchive.put(typeName, "main");
		}
		archiveIndexes.put("main", mergedIndex);
		return new ModuleIndex(mergedIndex, classToArchive, archiveIndexes);
	}

	/** The merged fallback index (all classes from all archives). */
	public IndexView index() {
		return mergedIndex;
	}

	/**
	 * Returns the {@link IndexView} for a single archive, or the merged fallback if
	 * no per-archive index was recorded.
	 */
	public IndexView archiveIndex(String archiveName) {
		IndexView idx = archiveIndexes.get(archiveName);
		return idx != null ? idx : mergedIndex;
	}

	/** All archive names. */
	public Set<String> archives() {
		return archiveIndexes.keySet();
	}

	/**
	 * All indexed type names, cached from the class-to-archive map built during
	 * index loading. Used for cross-archive {@code @ConditionalOnBean} visibility
	 * without re-iterating {@code IndexView.getKnownClasses()}.
	 */
	public Set<String> allTypeNames() {
		return allTypeNames;
	}

	/**
	 * Returns the archive name a class belongs to, or {@code null} if the class is
	 * not indexed. This is the boundary key for {@code @ConditionalOnBean}
	 * visibility: a condition is satisfied only by beans in the same archive.
	 */
	public String archiveOf(String className) {
		return classToArchive.get(className);
	}
}
