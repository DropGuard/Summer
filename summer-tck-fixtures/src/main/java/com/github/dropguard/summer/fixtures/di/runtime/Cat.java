package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class Cat implements Animal {
	@Override
	public String sound() {
		return "meow";
	}
}
