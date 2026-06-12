package summer.fixtures.tx.dummy;

import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class TxTestConfiguration {
	private static DataSource mockDataSource;
	private static Connection mockConnection;

	public static void initMocks() {
		mockDataSource = mock(DataSource.class);
		mockConnection = mock(Connection.class);
		try {
			when(mockDataSource.getConnection()).thenReturn(mockConnection);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static DataSource getMockDataSource() {
		return mockDataSource;
	}

	public static Connection getMockConnection() {
		return mockConnection;
	}

	@Bean
	public DataSource dataSource() {
		return mockDataSource;
	}
}
