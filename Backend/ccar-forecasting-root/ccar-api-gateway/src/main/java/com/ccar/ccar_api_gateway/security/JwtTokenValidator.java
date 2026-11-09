package com.ccar.ccar_api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtTokenValidator {

    private static final Logger log =
            LoggerFactory.getLogger(JwtTokenValidator.class);

    private final SecretKey secretKey;

    public JwtTokenValidator(
            @Value("${ccar.security.jwt.secret}") String secret) {

        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );

        log.info("JWT Token Validator initialized successfully");
    }

    public boolean isValid(String token) {

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug(
                    "JWT validated successfully for subject: {}",
                    claims.getSubject()
            );

            return true;

        } catch (JwtException | IllegalArgumentException e) {

            log.warn(
                    "JWT validation failed: {}",
                    e.getClass().getSimpleName()
            );

            return false;
        }
    }
}
