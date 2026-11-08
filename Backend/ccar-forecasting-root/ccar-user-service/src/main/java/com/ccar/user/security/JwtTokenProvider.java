
package com.ccar.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtTokenProvider(String secret,
                            long expirationSeconds) {

        byte[] keyBytes =
                Base64.getDecoder().decode(secret);

        if (keyBytes.length < 64) {
            throw new IllegalArgumentException(
                    "HS512 requires at least 64 bytes");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String username) {

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusSeconds(expirationSeconds)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String validateAndGetUsername(String token) {

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
 