package com.example.Centralized_product.controller;

import com.example.Centralized_product.service.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakService keycloakService;
    private final RestTemplate restTemplate;

    /**
     * Login via Keycloak (Password grant)
     */
    @PostMapping("/login/{realm}")
    public ResponseEntity<String> login(
            @PathVariable String realm,
            @RequestParam String clientId,
            @RequestParam String username,
            @RequestParam String password) {

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
     * Validate only Keycloak token (no DB check)
     */
    @GetMapping("/validate/{realm}")
    public ResponseEntity<?> validateToken(
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

        return ResponseEntity.ok("✅ Token valid for user=" + maybeUsername.get());
    }


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

        // Call Gateway API
        String gatewayUrl = "http://localhost:8085/gateway/check-access"; // your gateway URL
        Map<String, String> payload = Map.of(
                "username", username,
                "product", product,
                "realm", realm
        );

        ResponseEntity<ProductAccessResponse> gatewayResponse =
                restTemplate.postForEntity(gatewayUrl, payload, ProductAccessResponse.class);

        ProductAccessResponse access = gatewayResponse.getBody();
        if (access != null && access.isAllowed()) {
            // Redirect client to product URL
            String redirectUrl = access.getProductBaseUrl() + access.getProductUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 redirect
        } else {
            return ResponseEntity.status(403).body("Access denied for product " + product);
        }
    }

    // DTO for Gateway response
    @Data
    public static class ProductAccessResponse {
        private boolean allowed;
        private String productBaseUrl;
        private String productUri;
    }
}
