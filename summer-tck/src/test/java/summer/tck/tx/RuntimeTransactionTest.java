package summer.tck.tx;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.tx.dummy.TxTestConfiguration;

public class RuntimeTransactionTest extends AbstractTransactionTCK {

	@Override
	protected ApplicationContext createContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.registerComponent(TxTestConfiguration.class);
		ctx.initializeBeans();
		return ctx;
	}
}
