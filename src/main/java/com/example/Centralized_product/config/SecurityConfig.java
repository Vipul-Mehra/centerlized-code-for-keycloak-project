package com.example.Centralized_product.config;

import com.example.Centralized_product.service.DynamicJwtDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableMethodSecurity // enables @PreAuthorize, @Secured, etc.
@RequiredArgsConstructor
public class SecurityConfig {
    private final KeycloakRealmAndClientRoleConverter roleConverter;
    private final DynamicJwtDecoder dynamicJwtDecoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(roleConverter);

        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll() // signup/login allowed
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {
                            jwt.decoder(dynamicJwtDecoder);
                            jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
                        })
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // 👇 Add this to override Spring Boot’s default generated user
    @Bean
    public UserDetailsService userDetailsService() {
        // return empty in-memory store → no default user created
        return new InMemoryUserDetailsManager();
    }
}
