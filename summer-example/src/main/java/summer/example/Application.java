package summer.example;

import summer.boot.SummerApplication;
import summer.core.Engine;

public class Application {
	public static void main(String[] args) throws Exception {
		SummerApplication.run(Engine.AOT, args);
	}
}
