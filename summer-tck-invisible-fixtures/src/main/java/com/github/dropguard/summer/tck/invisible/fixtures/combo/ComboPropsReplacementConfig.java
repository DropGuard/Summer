package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;

/** Replaces {@link ComboPropsConfig} without producing {@link ComboProps} itself. */
@Configuration
@Replaces(ComboPropsConfig.class)
public class ComboPropsReplacementConfig {}
