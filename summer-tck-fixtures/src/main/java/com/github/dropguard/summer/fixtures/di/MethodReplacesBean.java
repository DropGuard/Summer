package com.github.dropguard.summer.fixtures.di;

/**
 * Bean for method-level @Replaces tests.
 */
public class MethodReplacesBean {

	private final String value;

	public MethodReplacesBean(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
