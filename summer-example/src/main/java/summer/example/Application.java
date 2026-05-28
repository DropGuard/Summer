package summer.example;

import summer.web.SummerApplication;

public class Application {
	public static void main(String[] args) throws Exception {
		SummerApplication.builder(Application.class).useAot().run(args);
	}
}