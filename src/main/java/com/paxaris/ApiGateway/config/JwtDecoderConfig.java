package com.paxaris.ApiGateway.config;


import com.paxaris.ApiGateway.service.DynamicJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        // No hardcoded issuer; it will be discovered per request from the token.
        return new DynamicJwtDecoder();
    }
}
