package summer.fixtures.di.conflict;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class ConflictConfig {

	@Bean
	public ConflictService conflictServiceImpl1() {
		return new ConflictServiceImpl1();
	}

	@Bean
	public ConflictService conflictServiceImpl2() {
		return new ConflictServiceImpl2();
	}

	@Bean
	public ConflictClient conflictClient(ConflictService conflictService) {
		return new ConflictClient(conflictService);
	}
}
