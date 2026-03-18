package com.anirudh.storage.minis3.auth.jwt.config;

import com.anirudh.storage.minis3.auth.jwt.properties.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author Anirudh
 * @since 23/02/26
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}
