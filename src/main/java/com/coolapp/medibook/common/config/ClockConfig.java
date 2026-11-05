package com.coolapp.medibook.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected everywhere "now" matters so rules (notice, cutoff, follow-up window) are testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
