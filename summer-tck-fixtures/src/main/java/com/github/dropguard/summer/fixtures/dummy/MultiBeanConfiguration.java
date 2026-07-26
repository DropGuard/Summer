package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

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
