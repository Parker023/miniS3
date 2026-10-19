package com.anirudh.storage.minis3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
public class Minis3StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(Minis3StorageServiceApplication.class, args);
    }

}
