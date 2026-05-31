package summer.tck.di.conflict;

import summer.core.Component;

@Component
public class ConflictClient {
	private final ConflictService conflictService;

	public ConflictClient(ConflictService conflictService) {
		this.conflictService = conflictService;
	}
}
