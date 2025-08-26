package com.example.Centralized_product.controller;

import com.example.Centralized_product.entities.User;
import com.example.Centralized_product.repository.UserRepository;
import com.example.Centralized_product.service.KeycloakService;
import com.example.Centralized_product.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final KeycloakService keycloakService; // 👉 ADD THIS

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/realms/master/protocol/openid-connect/token";

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", "mycompany-realm1111-realm");
        map.add("username", username);
        map.add("password", password);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, map, String.class);
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException e) {
            // Invalid credentials
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }


    // 👉 ADD THIS: Validates a Keycloak token and then checks your DB for permission
    @GetMapping("/validate/{realm}")
    public ResponseEntity<?> validateFromKeycloakAndDb(
            @PathVariable String realm,
            HttpServletRequest request
    ) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }

        String incomingToken = authHeader.substring("Bearer ".length());

        Optional<String> maybeUsername = keycloakService.validateAndGetUsername(realm, incomingToken);

        if (maybeUsername.isEmpty()) {
            return ResponseEntity.status(401).body("Invalid Keycloak token");
        }

        String username = maybeUsername.get();

        // ✅ Check DB for that user
        return userRepository.findByUsername(username)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok("Valid for realm=" + realm + ", user=" + username))
                .orElseGet(() -> ResponseEntity.status(403).body("User not provisioned in centralized DB"));
    }



}
