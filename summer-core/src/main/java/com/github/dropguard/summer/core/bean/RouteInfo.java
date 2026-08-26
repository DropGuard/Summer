package com.github.dropguard.summer.core.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Route metadata shared by both DI engines.
 *
 * <p>The fields here are the cross-engine contract: string identifiers ({@link #controllerClass},
 * {@link #methodName}) plus the parameter binding list. Both the Runtime and AOT engines read
 * exactly these strings — the AOT engine emits static handler calls from them, the Runtime engine
 * resolves the reflective {@code Method} from them at registration time (inside {@code
 * summer-runtime}, which is the only module permitted to hold a {@code java.lang.reflect.Method}).
 *
 * <p>This type deliberately holds no {@code java.lang.reflect.Method}: reflection types belong to
 * the runtime engine, never to the dual-engine shared layer in {@code summer-core}.
 */
public final class RouteInfo {

    /**
     * Parameter binding kinds. Exactly the set the framework produces and consumes — every constant
     * here flows end-to-end (scanner producer → AOT codegen consumer); the runtime engine re-reads
     * annotations reflectively and ignores this enum.
     *
     * <p>Pre-release trim: eight constants that had zero producers and zero consumers anywhere in
     * the codebase were removed rather than shipping dead public API.
     */
    public enum ParamBinding {
        /** Path segment parameter ({@code @PathParam}). */
        PATH,
        /** Query string parameter ({@code @QueryParam}). */
        QUERY,
        /** Request body parameter, optionally validated. */
        BODY,
        /**
         * Resolved through the HttpParameterResolverChain by parameter type — covers
         * Pageable/ScrollRequest/SseStream/ChunkedResponse/Request-shaped parameters whose
         * resolution is user-replaceable via {@code @Replaces} on their resolvers. Named for the
         * mechanism, not any single type: this bucket resolved far more than paging long before the
         * name said so.
         */
        RESOLVER
    }

    /** Method parameter binding metadata. */
    public static final class ParamInfo {
        /** Parameter name (for diagnostics), not the binding key. */
        public final String name;

        /** The binding key: {@code @PathParam}/{@code @QueryParam} value, or empty. */
        public final String bindingName;

        public final String type;
        public final ParamBinding binding;
        public final boolean validated;

        /**
         * @param bindingName the {@code @PathParam}/{@code @QueryParam} value (empty if none)
         */
        public ParamInfo(
                String name,
                String bindingName,
                String type,
                ParamBinding binding,
                boolean validated) {
            this.name = name;
            this.bindingName = bindingName;
            this.type = type;
            this.binding = binding;
            this.validated = validated;
        }

        @Override
        public String toString() {
            return (validated ? "@Valid " : "") + binding + " " + type + " " + name;
        }
    }

    public final String httpMethod;
    public final String path;
    public final String controllerClass;
    public final String methodName;

    /**
     * Parameter binding metadata, populated by the route scanners during container construction
     * (mutable list by design — scanners append as they walk methods). Read-only once the container
     * is built: no framework code mutates it after construction, and consumers must not.
     */
    public final List<ParamInfo> params = new ArrayList<>();

    /**
     * Builds route metadata from the cross-engine string contract. The runtime engine resolves the
     * controller {@link Class} from {@link #controllerClass} at registration time — this shared
     * type deliberately holds no {@code java.lang.reflect} reference (neither {@code Method} nor
     * {@code Class}), keeping the dual-engine layer reflection-free.
     */
    public RouteInfo(String httpMethod, String path, String controllerClass, String methodName) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.controllerClass = controllerClass;
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return httpMethod + " " + path + " -> " + controllerClass + "." + methodName;
    }
}
