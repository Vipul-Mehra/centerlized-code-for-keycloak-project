package com.paxaris.identity_service.controller;

import com.paxaris.identity_service.service.KeycloakService;
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
    public ResponseEntity<Map<String, String>> validateToken(
            @PathVariable String realm,
            HttpServletRequest request
    ) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
        }

        String token = authHeader.substring("Bearer ".length());
        Optional<String> username = keycloakService.validateAndGetUsername(realm, token);

        return username
                .map(s -> ResponseEntity.ok(Map.of("username", s)))
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid Keycloak token")));
    }


    // ===================== VALIDATE & AUTHORIZE =====================
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

        String token = authHeader.substring("Bearer ".length());
        Optional<String> usernameOpt = keycloakService.validateAndGetUsername(realm, token);

        if (usernameOpt.isEmpty()) return ResponseEntity.status(401).body("Invalid Keycloak token");

        String username = usernameOpt.get();
        List<String> roles = keycloakService.getRoleFromToken(token);
        if (roles.isEmpty()) return ResponseEntity.status(403).body("No roles assigned to user");

        // Call Gateway service to check access
        String gatewayUrl = "http://localhost:8085/access/check";
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("product", product);
        payload.put("realm", realm);
        payload.put("roles", roles);

        ProductAccessResponse access;
        try {
            access = restTemplate.postForEntity(gatewayUrl, payload, ProductAccessResponse.class).getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to connect to Gateway service");
        }

        if (access == null || !access.isAllowed()) return ResponseEntity.status(403).body("Access denied");

        String baseUrl = access.getProductUrl().endsWith("/") ?
                access.getProductUrl().substring(0, access.getProductUrl().length() - 1) :
                access.getProductUrl();
        String uriPart = (access.getProductUri() != null && !access.getProductUri().isBlank()) ?
                (access.getProductUri().startsWith("/") ? access.getProductUri() : "/" + access.getProductUri()) :
                "";
        String redirectUrl = baseUrl + uriPart;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirectUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @Data
    public static class ProductAccessResponse {
        private boolean allowed;
        private String message;
        private String productUrl;
        private String productUri;
        private List<String> roleName;
    }
}
