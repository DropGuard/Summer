package com.github.dropguard.summer.fixtures.di.generic;

public interface GenericService<T> {
	T process(T input);
}
