package com.moneymap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MoneyMap — self-hosted, privacy-first personal finance manager.
 * Architecture per PRD Section 00: Browser → Thymeleaf → Controllers → Services
 * → Repository interfaces → JSON files on disk (atomic writes) in DATA_DIR.
 */
@SpringBootApplication
@EnableScheduling
public class MoneyMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneyMapApplication.class, args);
    }
}
