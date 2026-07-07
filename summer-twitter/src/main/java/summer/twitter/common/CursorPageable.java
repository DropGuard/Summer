package summer.twitter.common;

import summer.web.ScrollRequest;

public record CursorPageable(Long cursor, int limit) implements ScrollRequest {
}
