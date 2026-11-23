package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** Present only when a {@link ComboFeature} bean exists — real or mocked. */
@Component
@ConditionalOnBean(ComboFeature.class)
public class FeatureGated {}
