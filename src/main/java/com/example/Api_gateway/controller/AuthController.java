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
            // Ask RestTemplate to convert JSON to Map automatically
            ResponseEntity<Map> response = restTemplate.postForEntity(url, map, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }




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

        // Call Gateway API to check role + subscription
        String gatewayUrl = "http://localhost:8085/gateway/check-access";
        Map<String, String> payload = Map.of(
                "username", username,
                "product", product,
                "realm", realm
        );

        ResponseEntity<ProductAccessResponse> gatewayResponse =
                restTemplate.postForEntity(gatewayUrl, payload, ProductAccessResponse.class);

        ProductAccessResponse access = gatewayResponse.getBody();
        if (access == null || !access.isAllowed()) {
            return ResponseEntity.status(403).body("Access denied for product " + product);
        }

        // ✅ Fetch product base URL directly from DB for safety
        String productBaseUrl = fetchProductBaseUrl(product);

        if (productBaseUrl == null || productBaseUrl.isBlank()) {
            return ResponseEntity.status(500).body("Product base URL not found for " + product);
        }

        // Build final redirect URL
        String redirectUrl = productBaseUrl + access.getProductUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirectUrl));

        return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 redirect
    }

    /**
     * Fetch product base URL from DB
     */
    private String fetchProductBaseUrl(String productName) {
        try {
            ResponseEntity<ProductDTO> productResponse = restTemplate.getForEntity(
                    "http://localhost:8085/products/" + productName, ProductDTO.class
            );
            ProductDTO product = productResponse.getBody();
            if (product != null) {
                return product.getProductUrl();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    // DTO for Gateway response
    @Data
    public static class ProductAccessResponse {
        private boolean allowed;
        private String productUrl;
        private String productUri;
    }
    @Data
    public static class ProductDTO {
        private String productName;
        private String productUrl;
        private String description;
    }

}
