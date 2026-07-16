package summer.twitter;

import summer.boot.SummerApplication;


public class Application {
	public static void main(String[] args) throws Exception {
		new SummerApplication()
			.apply(summer.web.middleware.CorsMiddleware.class)
			.apply(summer.twitter.auth.AuthMiddleware.class)
			.start(args);
	}
}
