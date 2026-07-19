package summer.realworld;

import summer.core.Component;
import summer.realworld.common.BusinessException;
import summer.realworld.user.UserDtos;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.ExceptionHandler;

/**
 * Global exception handler for the realworld demo.
 *
 * <p>
 * Business errors extend {@link BusinessException} and carry their own HTTP
 * status + field, so one handler renders them all (preserving the RealWorld
 * error shape {@code {"errors":{field:[message]}}}). Everything else falls
 * through to the catch-all, which returns a fixed 500 body and leaks no
 * internal detail. Controllers therefore never hand-write {@code try/catch}
 * for business errors.
 * </p>
 */
@Component
public class GlobalErrorHandler {

	@ExceptionHandler(BusinessException.class)
	public void handleBusiness(HttpContext ctx, BusinessException e) {
		UserDtos.ErrorResponse body = UserDtos.ErrorResponse.of(e.field(), e.getMessage());
		ctx.json(e.status(), body);
	}

	@ExceptionHandler(Exception.class)
	public void handleUnexpected(HttpContext ctx, Exception e) {
		ctx.json(HttpStatus.INTERNAL_SERVER_ERROR, UserDtos.ErrorResponse.of("error", "An unexpected error occurred"));
	}
}
