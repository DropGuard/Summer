package summer.tck.tx.dummy;

import java.sql.SQLException;

public interface TransactionalService {
	void doSuccess() throws SQLException;
	void doFailure() throws SQLException;
}
