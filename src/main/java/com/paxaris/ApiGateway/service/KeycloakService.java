package com.paxaris.ApiGateway.service;

import com.paxaris.ApiGateway.dto.RoleCreationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();

    // Get client UUID from Keycloak
    public String getClientId(String realm, String clientName, String token) {
        try {
            String url = "http://localhost:8080/admin/realms/" + realm + "/clients?clientId=" + clientName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            if (response.getBody() == null || response.getBody().isEmpty()) return null;

            Map<String, Object> clientData = (Map<String, Object>) response.getBody().get(0);
            return (String) clientData.get("id");

        } catch (Exception e) {
            log.error("Error fetching clientId: {}", e.getMessage());
            return null;
        }
    }

    // Create a new role for a client
    public boolean createRole(String realm, String clientId, RoleCreationRequest request, String token) {
        try {
            String url = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "name", request.getName(),
                    "description", request.getDescription()
            );
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return true;
        } catch (Exception e) {
            log.error("Error creating role: {}", e.getMessage());
            return false;
        }
    }

    // Update existing role
    public boolean updateRole(String realm, String clientId, String roleName, RoleCreationRequest request, String token) {
        System.out.println("reques of the update values" + realm + clientId + roleName + request);
        try {
            String url = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles/" + roleName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "name", request.getName(),
                    "description", request.getDescription()
            );
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
            return true;
        } catch (Exception e) {
            log.error("Error updating role: {}", e.getMessage());
            return false;
        }
    }

    // Delete a role
    public boolean deleteRole(String realm, String clientId, String roleName, String token) {
        try {
            String url = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles/" + roleName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            return true;
        } catch (Exception e) {
            log.error("Error deleting role: {}", e.getMessage());
            return false;
        }
    }

    // Validate JWT token and extract username
    public Optional<String> validateAndGetUsername(String realm, String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return Optional.ofNullable(claims.getSubject()); // returns "username"
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // Extract roles from JWT token
    public List<String> getRoleFromToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            Map<String, Object> realmAccess = (Map<String, Object>) claims.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                return (List<String>) realmAccess.get("roles");
            }
            return Collections.emptyList();
        } catch (ParseException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
