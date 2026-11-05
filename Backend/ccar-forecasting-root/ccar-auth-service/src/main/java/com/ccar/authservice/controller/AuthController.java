package com.ccar.authservice.controller;

import com.ccar.authservice.dto.LoginRequest;
import com.ccar.authservice.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwt;

    public AuthController(AuthenticationManager authManager, JwtTokenProvider jwt) {
        this.authManager = authManager;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        String token = jwt.generateToken(auth.getName(), roles);
        return Map.of("access_token", token,
                "token_type", "Bearer",
                "expires_in", jwt.getExpirationSeconds());
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(@RequestParam String token) {
        return Map.of("valid", jwt.validateToken(token));
    }
}

