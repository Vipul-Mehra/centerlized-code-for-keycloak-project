package com.paxaris.ApiGateway.service;

import com.paxaris.ApiGateway.dto.RoleRequest;
import com.paxaris.ApiGateway.dto.UrlEntry;
import com.paxaris.ApiGateway.dto.RoleCreationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String PRODUCT_SERVICE_URL = "http://localhost:8082";

    // ===================== CLIENT LOOKUP =====================
    public String getClientId(String realm, String client, String token) {
        try {
            String kcUrl = "http://localhost:8080/admin/realms/" + realm + "/clients?clientId=" + client;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> response = restTemplate.exchange(
                    kcUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);

            if (response.getBody() != null && !response.getBody().isEmpty()) {
                Map<String, Object> clientObj = (Map<String, Object>) response.getBody().get(0);
                return (String) clientObj.get("id"); // Keycloak returns UUID as "id"
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching clientId: {}", e.getMessage(), e);
            return null;
        }
    }

    // ===================== CREATE ROLE =====================
    public boolean createRole(String realm, String clientId, RoleCreationRequest request,
                              String token, String clientName) {
        try {
            // --- Create role in Keycloak ---
            String kcUrl = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> kcBody = Map.of(
                    "name", request.getName(),
                    "description", request.getDescription()
            );

            restTemplate.postForEntity(kcUrl, new HttpEntity<>(kcBody, headers), String.class);

            // --- Sync with ProjectManager ---
            String pmUrl = PRODUCT_SERVICE_URL + "/project/roles/save-or-update";

            RoleRequest roleRequest = new RoleRequest();
            roleRequest.setRealmName(realm);
            roleRequest.setProductName(clientName);
            roleRequest.setRoleName(request.getName());
            roleRequest.setUrls(List.of(new UrlEntry(null, request.getUrl(), request.getUri())));

            restTemplate.postForEntity(pmUrl, roleRequest, Void.class);

            return true;
        } catch (Exception e) {
            log.error("Error creating role in Keycloak/ProjectManager: {}", e.getMessage(), e);
            return false;
        }
    }

    // ===================== UPDATE ROLE =====================
    public boolean updateRole(String realm, String clientId, String oldRoleName,
                              RoleCreationRequest request, String token, String clientName) {
        try {
            // --- Update role in Keycloak ---
            String kcUrl = "http://localhost:8080/admin/realms/" + realm +
                    "/clients/" + clientId + "/roles/" + oldRoleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> kcBody = Map.of(
                    "name", request.getName(),
                    "description", request.getDescription()
            );

            restTemplate.exchange(kcUrl, HttpMethod.PUT, new HttpEntity<>(kcBody, headers), String.class);

            // --- Sync with ProjectManager ---
            String pmUrl = PRODUCT_SERVICE_URL + "/project/roles/save-or-update";

            RoleRequest roleRequest = new RoleRequest();
            roleRequest.setRealmName(realm);
            roleRequest.setProductName(clientName);
            roleRequest.setRoleName(request.getName());
            roleRequest.setUrls(List.of(new UrlEntry(null, request.getUrl(), request.getUri())));

            restTemplate.postForEntity(pmUrl, roleRequest, Void.class);

            return true;
        } catch (Exception e) {
            log.error("Error updating role in Keycloak/ProjectManager: {}", e.getMessage(), e);
            return false;
        }
    }

    // ===================== DELETE ROLE =====================
    public boolean deleteRole(String realm, String clientId, String roleName,
                              String token, String clientName) {
        try {
            // --- Delete role in Keycloak ---
            String kcUrl = "http://localhost:8080/admin/realms/" + realm +
                    "/clients/" + clientId + "/roles/" + roleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            restTemplate.exchange(kcUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

            // --- Delete role in ProjectManager ---
            String getUrl = PRODUCT_SERVICE_URL + "/project/roles";
            ResponseEntity<RoleRequest[]> response =
                    restTemplate.getForEntity(getUrl, RoleRequest[].class);

            if (response.getBody() != null) {
                for (RoleRequest role : response.getBody()) {
                    if (role.getRealmName().equals(realm)
                            && role.getProductName().equals(clientName)
                            && role.getRoleName().equals(roleName)) {
                        String deleteUrl = PRODUCT_SERVICE_URL + "/project/roles/" + role.getId();
                        restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
                        break;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error deleting role in Keycloak/ProjectManager: {}", e.getMessage(), e);
            return false;
        }
    }

    // ===================== TOKEN VALIDATION =====================
    public Optional<String> validateAndGetUsername(String realm, String token) {
        try {
            String userInfoUrl = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/userinfo";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object username = response.getBody().get("preferred_username");
                if (username != null) {
                    return Optional.of(username.toString());
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ===================== TOKEN ROLES EXTRACTION =====================
    public List<String> getRoleFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return List.of();

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> payload = new ObjectMapper().readValue(payloadJson, Map.class);

            // Realm roles
            Map<String, Object> realmAccess = (Map<String, Object>) payload.get("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                return (List<String>) realmAccess.get("roles");
            }

            // Client roles
            Map<String, Object> resourceAccess = (Map<String, Object>) payload.get("resource_access");
            if (resourceAccess != null) {
                List<String> roles = new ArrayList<>();
                for (Object value : resourceAccess.values()) {
                    Map<String, Object> entry = (Map<String, Object>) value;
                    if (entry.containsKey("roles")) {
                        roles.addAll((List<String>) entry.get("roles"));
                    }
                }
                return roles;
            }

            return List.of();
        } catch (Exception e) {
            log.error("Error extracting roles from token: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
