package summer.fixtures.di.conflict;

public class ConflictClient {
	private final ConflictService conflictService;

	public ConflictClient(ConflictService conflictService) {
		this.conflictService = conflictService;
	}
}
