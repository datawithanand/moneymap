package com.moneymap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Spring Security is used ONLY as a library for BCrypt (Section 00 §5.2) —
 * no filter chain, no UserDetailsService, no security auto-configuration.
 */
@Configuration
public class BeansConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);   // cost 10 (Section 16 — Security)
    }
}
