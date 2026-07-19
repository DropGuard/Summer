package summer.twitter;

import summer.core.Component;
import summer.twitter.common.BusinessException;
import summer.twitter.common.ErrorResponse;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.ExceptionHandler;

/**
 * Global exception handler for the twitter demo.
 *
 * <p>
 * Business errors extend {@link BusinessException} and already carry the HTTP
 * status + a stable error code, so one handler renders them all. Everything
 * else falls through to the catch-all, which returns a fixed 500 body and
 * leaks no internal detail (the real cause is logged by the framework).
 * </p>
 *
 * <p>
 * Controllers therefore never need hand-written {@code try/catch}: they call
 * services that throw typed exceptions, and this handler translates them.
 * </p>
 */
@Component
public class GlobalErrorHandler {

	@ExceptionHandler(BusinessException.class)
	public void handleBusiness(HttpContext ctx, BusinessException e) {
		ctx.json(e.status(), new ErrorResponse(e.code(), e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public void handleUnexpected(HttpContext ctx, Exception e) {
		ctx.json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.internalError());
	}
}
