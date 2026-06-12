package summer.fixtures.aop.metadata;

public interface MetadataSampleService {

	@MetadataTagged
	String taggedMethod();

	String plainMethod();

	@MetadataTagged
	String taggedWithArg(String arg);
}
