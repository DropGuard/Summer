package summer.boot;

/**
 * Application banner displayed on startup.
 */
final class Banner {

	static String format() {
		String version = Banner.class.getPackage().getImplementationVersion();
		String versionSuffix = version != null ? " (v" + version + ")" : "";

		return """

			       _____
			      / ___/__  ______ ___  ____ ___  ___  _____
			      \\__ \\/ / / / __ `__ \\/ __ `__ \\/ _ \\/ ___/
			     ___/ / /_/ / / / / / / / / / / /  __/ /
			    /____/\\__,_/_/ /_/ /_/_/ /_/ /_/\\___/_/

			     :: Summer Framework ::%s
			    """.formatted(versionSuffix);
	}

	private Banner() {
	}
}
