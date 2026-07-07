package summer.runtime;

import summer.web.ScrollRequest;

public record DefaultPageRequest(int page, int size) implements ScrollRequest {
}
