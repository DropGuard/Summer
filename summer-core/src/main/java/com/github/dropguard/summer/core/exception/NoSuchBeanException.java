package com.github.dropguard.summer.core.exception;

import com.github.dropguard.summer.core.ErrorCode;
import java.util.List;

/**
 * Thrown when a required bean cannot be found in the container.
 *
 * <p>Carries the structured context of the failure (what was missing, who needed it, the registered
 * candidates, and any candidate of the missing type that assembly withdrew — with the reason) as
 * fields — not just a hand-built message — so logs and tests consume the data uniformly. The
 * human-readable message is assembled in exactly one place (the structured constructor); callers
 * pass the pieces rather than building strings themselves.
 */
public class NoSuchBeanException extends SummerException {

    public final String missingType;
    public final String dependentBean;
    public final List<String> candidates;
    public final List<String> nearMisses;

    /**
     * Candidates of the missing type that were discovered but withdrawn during assembly, each as
     * {@code "candidate (reason)"} — the answer to "why is it not there?".
     */
    public final List<String> withdrawn;

    /** Plain-message form, retained for callers that pre-format (e.g. maven plugin). */
    public NoSuchBeanException(String message) {
        super(ErrorCode.BEAN_NOT_FOUND, message);
        this.missingType = null;
        this.dependentBean = null;
        this.candidates = List.of();
        this.nearMisses = List.of();
        this.withdrawn = List.of();
    }

    public NoSuchBeanException(
            String missingType,
            String dependentBean,
            List<String> candidates,
            List<String> nearMisses) {
        this(missingType, dependentBean, candidates, nearMisses, List.of());
    }

    public NoSuchBeanException(
            String missingType,
            String dependentBean,
            List<String> candidates,
            List<String> nearMisses,
            List<String> withdrawn) {
        super(
                ErrorCode.BEAN_NOT_FOUND,
                render(missingType, dependentBean, candidates, nearMisses, withdrawn));
        this.missingType = missingType;
        this.dependentBean = dependentBean;
        this.candidates = candidates;
        this.nearMisses = nearMisses;
        this.withdrawn = withdrawn;
    }

    /**
     * This failure, explained by the withdrawn candidates of its missing type. Returns {@code this}
     * when there is nothing to add (no withdrawals, or the plain-message form).
     */
    public NoSuchBeanException withWithdrawn(List<String> withdrawnOfMissingType) {
        if (missingType == null
                || withdrawnOfMissingType == null
                || withdrawnOfMissingType.isEmpty()) {
            return this;
        }
        NoSuchBeanException explained =
                new NoSuchBeanException(
                        missingType,
                        dependentBean,
                        candidates,
                        nearMisses,
                        List.copyOf(withdrawnOfMissingType));
        explained.setStackTrace(getStackTrace());
        return explained;
    }

    private static String render(
            String missingType,
            String dependentBean,
            List<String> candidates,
            List<String> nearMisses,
            List<String> withdrawn) {
        StringBuilder sb = new StringBuilder();
        sb.append("No bean found for dependency type: ")
                .append(missingType)
                .append(" required by ")
                .append(dependentBean);
        for (String w : withdrawn) {
            sb.append("\n  withdrawn during assembly: ").append(w);
        }
        sb.append("\n  registered bean types (").append(candidates.size()).append("): ");
        int limit = Math.min(candidates.size(), 25);
        sb.append(String.join(", ", candidates.subList(0, limit)));
        if (candidates.size() > limit) {
            sb.append(", ...");
        }
        if (!nearMisses.isEmpty()) {
            sb.append("\n  candidates with matching simple name '")
                    .append(simpleName(missingType))
                    .append("': ")
                    .append(String.join(", ", nearMisses));
        }
        return sb.toString();
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
    }
}
