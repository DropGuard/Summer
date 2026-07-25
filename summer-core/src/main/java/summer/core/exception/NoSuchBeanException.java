package summer.core.exception;

import java.util.List;
import summer.core.ErrorCode;

/**
 * Thrown when a required bean cannot be found in the container.
 *
 * <p>
 * Carries the structured context of the failure (what was missing, who needed
 * it, and the registered candidates) as fields — not just a hand-built message
 * — so logs and tests consume the data uniformly. The human-readable message is
 * assembled in exactly one place (the structured constructor); callers pass the
 * pieces rather than building strings themselves.
 * </p>
 */
public class NoSuchBeanException extends SummerException {

	public final String missingType;
	public final String dependentBean;
	public final List<String> candidates;
	public final List<String> nearMisses;

	/**
	 * Plain-message form, retained for callers that pre-format (e.g. maven plugin).
	 */
	public NoSuchBeanException(String message) {
		super(ErrorCode.BEAN_NOT_FOUND, message);
		this.missingType = null;
		this.dependentBean = null;
		this.candidates = List.of();
		this.nearMisses = List.of();
	}

	public NoSuchBeanException(String missingType, String dependentBean, List<String> candidates,
			List<String> nearMisses) {
		super(ErrorCode.BEAN_NOT_FOUND, render(missingType, dependentBean, candidates, nearMisses));
		this.missingType = missingType;
		this.dependentBean = dependentBean;
		this.candidates = candidates;
		this.nearMisses = nearMisses;
	}

	private static String render(String missingType, String dependentBean, List<String> candidates,
			List<String> nearMisses) {
		StringBuilder sb = new StringBuilder();
		sb.append("No bean found for dependency type: ").append(missingType).append(" required by ")
				.append(dependentBean);
		sb.append("\n  registered bean types (").append(candidates.size()).append("): ");
		int limit = Math.min(candidates.size(), 25);
		sb.append(String.join(", ", candidates.subList(0, limit)));
		if (candidates.size() > limit) {
			sb.append(", ...");
		}
		if (!nearMisses.isEmpty()) {
			sb.append("\n  candidates with matching simple name '").append(simpleName(missingType)).append("': ")
					.append(String.join(", ", nearMisses));
		}
		return sb.toString();
	}

	private static String simpleName(String fqcn) {
		int idx = fqcn.lastIndexOf('.');
		return idx < 0 ? fqcn : fqcn.substring(idx + 1);
	}
}
