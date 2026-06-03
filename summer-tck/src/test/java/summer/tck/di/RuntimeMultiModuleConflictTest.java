package summer.tck.di;

import summer.runtime.RuntimeDiEngine;
import summer.tck.di.conflict.ConflictClient;

public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerConflict() {
		new RuntimeDiEngine().create(ConflictClient.class);
	}
}
