package com.example.Centralized_product.controller;

import com.example.Centralized_product.repository.UserRepository;
import com.example.Centralized_product.repository.SubscriptionRepository;
import com.example.Centralized_product.service.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Login user via Keycloak (password grant).
     */
    @PostMapping("/login/{realm}")
    public ResponseEntity<String> login(
            @PathVariable String realm,
            @RequestParam String clientId,
            @RequestParam String username,
            @RequestParam String password) {

        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", clientId);
        map.add("username", username);
        map.add("password", password);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, map, String.class);
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }

    /**
     * Validate token against Keycloak and check centralized DB for provisioning.
     */
    @GetMapping("/validate/{realm}/{product}")
    public ResponseEntity<?> validateFromKeycloakAndDb(
            @PathVariable String realm,
            @PathVariable String product,
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

        // ✅ Check subscription DB for that user + product
        boolean hasAccess = subscriptionRepository
                .existsByClientClientNameAndProductProductName(username, product);

        if (!hasAccess) {
            return ResponseEntity.status(403).body("User not subscribed to product=" + product);
        }

        return ResponseEntity.ok("✅ Valid for realm=" + realm + ", user=" + username + ", product=" + product);
    }

}
