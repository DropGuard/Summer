package summer.tck.di;

import summer.runtime.RuntimeApplicationContext;
import summer.tck.di.conflict.ConflictClient;

public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerFailure() {
		RuntimeApplicationContext.create(ConflictClient.class);
	}
}
