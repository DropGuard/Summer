package com.github.dropguard.summer.fixtures.validation;

public class TlsService {
	private final TlsConfig config;

	public TlsService(TlsConfig config) {
		this.config = config;
	}

	public TlsConfig getConfig() {
		return config;
	}
}
