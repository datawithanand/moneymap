package com.moneymap.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson conventions per PRD Section 17 §12.2: ISO-8601 UTC timestamps (never epoch numbers),
 * unknown properties tolerated on read (forward compatibility for imports), pretty-printed files
 * so the self-hosting operator can inspect their data on disk (Section 00's inspectability principle).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer moneyMapJacksonCustomizer() {
        return builder -> builder
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToEnable(SerializationFeature.INDENT_OUTPUT);
    }
}
