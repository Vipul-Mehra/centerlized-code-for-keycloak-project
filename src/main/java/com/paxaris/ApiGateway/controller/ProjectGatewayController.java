package com.paxaris.ApiGateway.controller;

import com.paxaris.ApiGateway.dto.RealmProductRoleUrl;
import com.paxaris.ApiGateway.dto.RoleCreationRequest;
import com.paxaris.ApiGateway.service.KeycloakService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectGatewayController {

    private final RestTemplate restTemplate;
    private final KeycloakService keycloakService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------- REGISTER ROLES ----------------
    @PostMapping("/register/{realm}/{client}")
    public ResponseEntity<String> registerProject(
            @PathVariable String realm,
            @PathVariable String client,
            @RequestBody List<RoleCreationRequest> roleRequests,
            @RequestHeader("Authorization") String authHeader) {

        // 1️⃣ Get client UUID from Keycloak
        String clientsUrl = "http://localhost:8080/admin/realms/" + realm + "/clients?clientId=" + client;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List> clientsResponse = restTemplate.exchange(clientsUrl, HttpMethod.GET, entity, List.class);
        if (clientsResponse.getBody() == null || clientsResponse.getBody().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Could not find client: " + client);
        }
        Map<String, Object> clientData = (Map<String, Object>) clientsResponse.getBody().get(0);
        String clientId = (String) clientData.get("id");

        // 2️⃣ Create roles in Keycloak and collect only successfully created roles
        List<RealmProductRoleUrl> pmRoles = new ArrayList<>();
        for (RoleCreationRequest role : roleRequests) {
            Map<String, Object> roleBody = Map.of(
                    "name", role.getName(),
                    "description", role.getDescription()
            );
            try {
                ResponseEntity<String> kcResponse = restTemplate.postForEntity(
                        "http://localhost:8080/admin/realms/" + realm + "/clients/" + clientId + "/roles",
                        new HttpEntity<>(roleBody, headers),
                        String.class
                );

                if (kcResponse.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Created role '{}' in client '{}'", role.getName(), client);

                    RealmProductRoleUrl dbRole = new RealmProductRoleUrl();
                    dbRole.setRealmName(realm);
                    dbRole.setProductName(client);
                    dbRole.setRoleName(role.getName());
                    dbRole.setUrl(role.getUrl() != null ? role.getUrl() : "");
                    dbRole.setUri(role.getUri() != null ? role.getUri() : "");
                    pmRoles.add(dbRole);
                } else {
                    log.error("❌ Failed creating role '{}' in Keycloak: {}", role.getName(), kcResponse.getBody());
                }
            } catch (Exception e) {
                log.error("❌ Exception creating role '{}' in Keycloak: {}", role.getName(), e.getMessage());
            }
        }

        // 3️⃣ Send only successfully created roles to Project Manager DB
        if (!pmRoles.isEmpty()) {
            try {
                String pmUrl = "http://localhost:8085/project/sync-roles";
                HttpHeaders pmHeaders = new HttpHeaders();
                pmHeaders.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<String> pmResponse = restTemplate.postForEntity(
                        pmUrl, new HttpEntity<>(pmRoles, pmHeaders), String.class
                );
                log.info("✅ Sent roles to Project Manager: {}", pmResponse.getBody());
            } catch (Exception e) {
                log.error("❌ Failed sending roles to Project Manager: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok("✅ Finished processing roles for client: " + client);
    }

    // ---------------- SYNC ROLES FROM KEYCLOAK ----------------
    @PostMapping("/sync-roles/{realm}/{client}")
    public ResponseEntity<String> syncRoles(
            @PathVariable String realm,
            @PathVariable String client,
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            // 1️⃣ Fetch roles from Keycloak service
            List<Map<String, Object>> kcRoles = keycloakService.getAllRoles(realm, client, token);

            List<RealmProductRoleUrl> pmRoles = kcRoles.stream().map(r -> {
                RealmProductRoleUrl role = new RealmProductRoleUrl();
                role.setRealmName(realm);
                role.setProductName(client);
                role.setRoleName((String) r.get("name"));
                role.setUrl(r.getOrDefault("url", "").toString());
                role.setUri(r.getOrDefault("uri", "").toString());
                return role;
            }).collect(Collectors.toList());

            // 🔹 LOG what is being sent to Project Manager
            String jsonPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pmRoles);
            log.info("📤 Sending the following payload to Project Manager:\n{}", jsonPayload);

            // 2️⃣ Sync roles to ProjectManager DB
            String pmUrl = "http://localhost:8085/project/sync-roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> pmResponse = restTemplate.postForEntity(pmUrl, new HttpEntity<>(pmRoles, headers), String.class);

            log.info("✅ Response from Project Manager: {}", pmResponse.getBody());
            return ResponseEntity.ok("✅ Synced roles to ProjectManager: " + pmResponse.getBody());
        } catch (Exception e) {
            log.error("❌ Error syncing roles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Error: " + e.getMessage());
        }
    }
}
