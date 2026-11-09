package com.ccar.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ccar.user.security.JwtTokenProvider;

@Configuration
public class JwtConfig {

    @Value("${ccar.security.jwt.secret}")
    private String jwtSecret;

    @Value("${ccar.security.jwt.expiration-seconds:3600}")
    private long jwtExpirationSeconds;

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(
                jwtSecret,
                jwtExpirationSeconds
        );
    }
}