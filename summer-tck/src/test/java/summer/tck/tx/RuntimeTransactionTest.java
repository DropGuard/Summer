package summer.tck.tx;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.tx.dummy.TxTestConfiguration;

public class RuntimeTransactionTest extends AbstractTransactionTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.builder().registerComponent(TxTestConfiguration.class).build();
	}
}
