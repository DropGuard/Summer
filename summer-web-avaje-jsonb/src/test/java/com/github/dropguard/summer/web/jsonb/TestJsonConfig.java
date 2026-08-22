package com.github.dropguard.summer.web.jsonb;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;
import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.JsonBodyConverter;

@Configuration
public class TestJsonConfig {

    @Bean
    @Replaces(JsonBodyConverter.class)
    public BodyConverter bodyConverter() {
        return new AvajeJsonbBodyConverter();
    }
}
