package summer.tck.tx;

import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.tx.dummy.TxTestConfiguration;

public class RuntimeTransactionTest extends AbstractTransactionTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(TxTestConfiguration.class);
	}
}
