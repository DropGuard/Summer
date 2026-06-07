package summer.tck.tx;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.tx.dummy.TxTestConfiguration;

public class RuntimeTransactionTest extends AbstractTransactionTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create(TxTestConfiguration.class);
	}
}
