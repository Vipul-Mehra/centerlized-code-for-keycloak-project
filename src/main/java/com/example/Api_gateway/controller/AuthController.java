package com.example.Api_gateway.controller;

import com.example.Api_gateway.service.KeycloakService;
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

    // ===================== LOGIN =====================
    @PostMapping("/login/{realm}")
    public ResponseEntity<Map<String, Object>> login(
            @PathVariable String realm,
            @RequestParam String clientId,
            @RequestParam(required = false) String clientSecret,
            @RequestParam String username,
            @RequestParam String password) {

        String url = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/token";
        System.out.println("🔑 LOGIN REQUEST: realm=" + realm + ", clientId=" + clientId + ", username=" + username);

        var map = new org.springframework.util.LinkedMultiValueMap<String, String>();
        map.add("grant_type", "password");
        map.add("client_id", clientId);
        if (clientSecret != null) map.add("client_secret", clientSecret);
        map.add("username", username);
        map.add("password", password);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, map, Map.class);
            System.out.println("✅ LOGIN SUCCESS: " + response.getBody());
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException e) {
            System.out.println("❌ LOGIN FAILED: " + e.getMessage());
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
        System.out.println("🔎 VALIDATE TOKEN: realm=" + realm + ", authHeader=" + authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }

        String incomingToken = authHeader.substring("Bearer ".length());
        Optional<String> maybeUsername = keycloakService.validateAndGetUsername(realm, incomingToken);

        if (maybeUsername.isEmpty()) {
            System.out.println("❌ INVALID TOKEN for realm=" + realm);
            return ResponseEntity.status(401).body("Invalid Keycloak token");
        }

        System.out.println("✅ TOKEN VALID for user=" + maybeUsername.get());
        return ResponseEntity.ok("✅ Token valid for user=" + maybeUsername.get());
    }

    // ===================== VALIDATE & AUTHORIZE =====================
    @GetMapping("/validate/{realm}/{product}")
    public ResponseEntity<?> validateAndAuthorize(
            @PathVariable String realm,
            @PathVariable String product,
            HttpServletRequest request
    ) {
        System.out.println("\n============================");
        System.out.println("🔎 VALIDATE & AUTHORIZE");
        System.out.println("Realm=" + realm + ", Product=" + product);

        // 1️⃣ Extract token
        String authHeader = request.getHeader("Authorization");
        System.out.println("AuthHeader=" + authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid Authorization header");
        }

        String incomingToken = authHeader.substring("Bearer ".length());
        Optional<String> maybeUsername = keycloakService.validateAndGetUsername(realm, incomingToken);

        if (maybeUsername.isEmpty()) {
            System.out.println("❌ INVALID TOKEN for realm=" + realm);
            return ResponseEntity.status(401).body("Invalid Keycloak token");
        }

        String username = maybeUsername.get();
        System.out.println("✅ TOKEN belongs to username=" + username);

        // 2️⃣ Call Gateway service
        String gatewayUrl = "http://localhost:8085/gateway/check-access";
        Map<String, String> payload = Map.of(
                "username", username,
                "product", product,
                "realm", realm
        );
        System.out.println("➡️ Sending payload to Gateway: " + payload);

        ResponseEntity<ProductAccessResponse> gatewayResponse;
        try {
            gatewayResponse = restTemplate.postForEntity(gatewayUrl, payload, ProductAccessResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to connect to Gateway service");
        }

        ProductAccessResponse access = gatewayResponse.getBody();
        System.out.println("⬅️ Gateway Response: " + access);

        if (access == null || !access.isAllowed()) {
            System.out.println("❌ ACCESS DENIED for product=" + product);
            return ResponseEntity.status(403).body("Access denied for product " + product);
        }

        if (access.getProductUrl() == null || access.getProductUrl().isBlank()) {
            System.out.println("❌ Product base URL not found for " + product);
            return ResponseEntity.status(500).body("Product base URL not found for " + product);
        }

        // 3️⃣ Normalize redirect URL
        String baseUrl = access.getProductUrl().endsWith("/") ?
                access.getProductUrl().substring(0, access.getProductUrl().length() - 1) :
                access.getProductUrl();

        String uriPart = (access.getProductUri() != null && !access.getProductUri().isBlank())
                ? (access.getProductUri().startsWith("/") ? access.getProductUri() : "/" + access.getProductUri())
                : "";

        String redirectUrl = baseUrl + uriPart;
        System.out.println("✅ REDIRECT URL: " + redirectUrl);

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
        private String roleName;

        @Override
        public String toString() {
            return "ProductAccessResponse{" +
                    "allowed=" + allowed +
                    ", message='" + message + '\'' +
                    ", productUrl='" + productUrl + '\'' +
                    ", productUri='" + productUri + '\'' +
                    ", roleName='" + roleName + '\'' +
                    '}';
        }
    }

    @Data
    public static class ProductDTO {
        private String productName;
        private String productUrl;
        private String description;
    }
}
