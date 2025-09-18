package com.paxaris.ApiGateway.service;

import com.paxaris.ApiGateway.dto.RoleCreationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.*;

@Slf4j
@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String PRODUCT_SERVICE_URL = "http://localhost:8085"; // ProductService base URL

    // ===================== GET CLIENT ID =====================
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

    // ===================== CREATE ROLE =====================
    public boolean createRole(String realm, String clientId, RoleCreationRequest request, String token, String clientName) {
        try {
            // --- Create in Keycloak ---
            String kcUrl = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "name", request.getName(),
                    "description", request.getDescription()
            );
            restTemplate.postForEntity(kcUrl, new HttpEntity<>(body, headers), String.class);

            // --- Sync with ProductService ---
            String psUrl = PRODUCT_SERVICE_URL + "/project/register";
            Map<String, Object> psPayload = Map.of(
                    "realmName", realm,
                    "productName", clientName,
                    "roleName", request.getName(),
                    "url", request.getUrl(),
                    "uri", request.getUri()
            );
            log.info("🔄 Processing role: realm={}, product={}, role={}, url={}, uri={}",
                    realm,clientName,
                    request.getName(),
                    request.getUrl(),
                    request.getUri()
            );
            restTemplate.postForEntity(psUrl, psPayload, Void.class);

            return true;
        } catch (Exception e) {
            log.error("Error creating role: {}", e.getMessage());
            return false;
        }
    }

    // ===================== UPDATE ROLE =====================
    public boolean updateRole(String realm, String clientId, String oldRoleName, RoleCreationRequest request, String token,String clientName) {
        try {
            // --- Update in Keycloak ---
            String kcUrl = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles/" + oldRoleName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "name", request.getName(),
                    "description", request.getDescription()
            );
            restTemplate.exchange(kcUrl, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);

            // --- Sync with Product Manager ---
            Map<String, Object> pmPayload = Map.of(
                    "realmName", realm,
                    "productName", clientName,
                    "oldRoleName", oldRoleName,
                    "newRoleName", request.getName(),
                    "url", request.getUrl(),
                    "uri", request.getUri()
            );

            log.info("Updating role in Project Manager: {}", pmPayload);
            String pmUrl = PRODUCT_SERVICE_URL + "/project/sync-roles";
            restTemplate.postForEntity(pmUrl, List.of(pmPayload), Void.class);

            return true;
        } catch (Exception e) {
            log.error("Error updating role: {}", e.getMessage(), e);
            return false;
        }
    }

    // ===================== DELETE ROLE =====================
    public boolean deleteRole(String realm, String clientId, String roleName, String token) {
        try {
            // --- Delete from Keycloak ---
            String kcUrl = "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles/" + roleName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(kcUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

            // --- Delete from ProductService ---
            String psUrl = PRODUCT_SERVICE_URL + "/project/delete-role/" + realm + "/" + clientId + "/" + roleName;
            restTemplate.exchange(psUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

            return true;
        } catch (Exception e) {
            log.error("Error deleting role: {}", e.getMessage());
            return false;
        }
    }

    // ===================== JWT HELPERS =====================
    public Optional<String> validateAndGetUsername(String realm, String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return Optional.empty();
        }
    }

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
            log.error("Error parsing roles from token: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
