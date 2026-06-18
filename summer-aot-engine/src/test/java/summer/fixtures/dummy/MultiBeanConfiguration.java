package summer.fixtures.dummy;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class MultiBeanConfiguration {

	@Bean
	public PlainServiceA plainServiceA() {
		return new PlainServiceA();
	}

	@Bean
	public PlainServiceB plainServiceB() {
		return new PlainServiceB();
	}
}
