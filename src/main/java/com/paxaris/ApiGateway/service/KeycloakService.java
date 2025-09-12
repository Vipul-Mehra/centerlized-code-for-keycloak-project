package com.paxaris.ApiGateway.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Validate token and extract usernameotepad
     */
    public Optional<String> validateAndGetUsername(String realm, String token) {
        try {
            // Decode JWT token to get username
            // Add your JWT verification logic here if needed
            // For simplicity, returning token as username
            return Optional.of(token);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Extract roles from JWT token
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoleFromToken(String token) {
        try {
            // JWT parsing logic (same as before)
            return Collections.emptyList(); // placeholder
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * ✅ Get all roles for a client from Keycloak
     */
    public List<Map<String, Object>> getAllRoles(String realm, String clientName, String token) {
        try {
            // 1️⃣ Get client UUID first
            String clientsUrl = "http://localhost:8080/admin/realms/" + realm + "/clients?clientId=" + clientName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> clientsResponse = restTemplate.exchange(clientsUrl, HttpMethod.GET, entity, List.class);
            if (clientsResponse.getBody() == null || clientsResponse.getBody().isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, Object> clientData = (Map<String, Object>) clientsResponse.getBody().get(0);
            String clientId = (String) clientData.get("id");

            // 2️⃣ Get roles for the client
            String rolesUrl = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles";
            ResponseEntity<List> rolesResponse = restTemplate.exchange(rolesUrl, HttpMethod.GET, entity, List.class);

            if (rolesResponse.getBody() == null) return Collections.emptyList();
            return rolesResponse.getBody();

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
