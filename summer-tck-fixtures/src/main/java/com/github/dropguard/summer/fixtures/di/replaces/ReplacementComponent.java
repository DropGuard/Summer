package com.github.dropguard.summer.fixtures.di.replaces;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Replaces;

@Replaces(OriginalComponent.class)
@Component
public class ReplacementComponent implements ReplacableService {

	@Override
	public String serve() {
		return "replacement";
	}
}
