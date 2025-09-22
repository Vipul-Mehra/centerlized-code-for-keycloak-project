package com.paxaris.ApiGateway.controller;

import com.paxaris.ApiGateway.service.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakService keycloakService;
    private final RestTemplate restTemplate;

    // ===================== LOGIN =====================
    @PostMapping("/login/{realm}")
    public ResponseEntity<Map<String, Object>> login(
            @PathVariable String realm,
            @RequestParam String clientId,
            @RequestParam(required = false) String clientSecret,
            @RequestParam String username,
            @RequestParam String password) {

        String url = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/token";

        var map = new org.springframework.util.LinkedMultiValueMap<String, String>();
        map.add("grant_type", "password");
        map.add("client_id", clientId);
        if (clientSecret != null) map.add("client_secret", clientSecret);
        map.add("username", username);
        map.add("password", password);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, map, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    // ===================== TOKEN VALIDATION =====================
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

    // ===================== VALIDATE & AUTHORIZE =====================
    @GetMapping("/validate/{realm}/{product}")
    public ResponseEntity<?> validateAndAuthorize(
            @PathVariable String realm,
            @PathVariable String product,
            HttpServletRequest request
    ) {

        // Extract token
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

        // Extract all roles from token
        List<String> roleName = keycloakService.getRoleFromToken(incomingToken);
        if (roleName.isEmpty()) {
            return ResponseEntity.status(403).body("No roles assigned to user");
        }

        // Call Gateway service once with all roles
        String gatewayUrl = "http://localhost:8085/access/check";
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("product", product);
        payload.put("realm", realm);
        payload.put("roles", roleName); // <-- send all roles in a single request

        ProductAccessResponse access;
        try {
            access = restTemplate.postForEntity(gatewayUrl, payload, ProductAccessResponse.class).getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to connect to Gateway service");
        }

        if (access == null || !access.isAllowed()) {
            return ResponseEntity.status(403).body("Access denied for product " + product);
        }

        if (access.getProductUrl() == null || access.getProductUrl().isBlank()) {
            return ResponseEntity.status(500).body("Product base URL not found for " + product);
        }

        // Build redirect URL
        String baseUrl = access.getProductUrl().endsWith("/") ?
                access.getProductUrl().substring(0, access.getProductUrl().length() - 1) :
                access.getProductUrl();

        String uriPart = (access.getProductUri() != null && !access.getProductUri().isBlank())
                ? (access.getProductUri().startsWith("/") ? access.getProductUri() : "/" + access.getProductUri())
                : "";

        String redirectUrl = baseUrl + uriPart;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirectUrl));

        return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 redirect
    }

    // ===================== DTOs =====================
    @Data
    public static class ProductAccessResponse {
        private boolean allowed;
        private String message;
        private String productUrl;
        private String productUri;
        private List<String> roleName;
    }


}