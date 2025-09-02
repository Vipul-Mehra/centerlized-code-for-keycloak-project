package com.example.Centralized_product.controller;

import com.example.Centralized_product.service.KeycloakService;
import com.example.Centralized_product.service.RedirectService;
import com.example.Centralized_product.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakService keycloakService;
    private final SubscriptionRepository subscriptionRepository;
    private final RedirectService redirectService;

    /**
     * Login via Keycloak (Password grant).
     */
    @PostMapping("/login/{realm}")
    public ResponseEntity<String> login(
            @PathVariable String realm,
            @RequestParam String clientId,
            @RequestParam String username,
            @RequestParam String password) {

        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/token";

        var map = new org.springframework.util.LinkedMultiValueMap<String, String>();
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
     * Validate token + check subscription.
     */
    @GetMapping("/validate/{realm}/{product}")
    public ResponseEntity<?> validateAndAuthorize(
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

        // Check subscription
        boolean hasAccess = subscriptionRepository
                .existsByClientClientNameAndProductProductName(username, product);

        if (!hasAccess) {
            return ResponseEntity.status(403).body("User not subscribed to product=" + product);
        }

        // Return product URL instead of redirecting
        String productUrl = redirectService.getRedirectUrl(product);

        return ResponseEntity.ok().body(
                "✅ User authorized. Product URL = " + productUrl
        );
    }
}
