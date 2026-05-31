package summer.tck.tx.dummy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import summer.core.Component;
import summer.tx.ThreadLocalTransactionContext;
import summer.tx.Transactional;

@Component
public class TransactionalServiceImpl implements TransactionalService {

	@Transactional
	@Override
	public void doSuccess() throws SQLException {
		Connection conn = ThreadLocalTransactionContext.getCurrentConnection();
		if (conn == null) {
			throw new IllegalStateException("No active transaction connection found");
		}
		// Simulate database interaction
		try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (?, ?)")) {
			// no-op
		}
	}

	@Transactional
	@Override
	public void doFailure() throws SQLException {
		Connection conn = ThreadLocalTransactionContext.getCurrentConnection();
		if (conn == null) {
			throw new IllegalStateException("No active transaction connection found");
		}
		throw new RuntimeException("Simulated DB failure");
	}
}
