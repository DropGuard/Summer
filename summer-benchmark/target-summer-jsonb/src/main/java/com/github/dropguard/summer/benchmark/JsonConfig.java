package com.github.dropguard.summer.benchmark;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;
import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.JsonBodyConverter;
import com.github.dropguard.summer.web.jsonb.AvajeJsonbBodyConverter;

@Configuration
public class JsonConfig {

    @Bean
    @Replaces(JsonBodyConverter.class)
    public BodyConverter bodyConverter() {
        return new AvajeJsonbBodyConverter();
    }
}
