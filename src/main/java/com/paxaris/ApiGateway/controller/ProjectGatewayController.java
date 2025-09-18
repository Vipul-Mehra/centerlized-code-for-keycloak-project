package com.paxaris.ApiGateway.controller;

import com.paxaris.ApiGateway.dto.RealmProductRoleUrl;
import com.paxaris.ApiGateway.dto.RoleCreationRequest;
import com.paxaris.ApiGateway.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectGatewayController {

    private final RestTemplate restTemplate;
    private final KeycloakService keycloakService;

    // ---------------- CREATE ROLE ----------------
    @PostMapping("/roles/{realm}/{client}")
    public ResponseEntity<String> createRole(
            @PathVariable String realm,
            @PathVariable String client,
            @RequestBody RoleCreationRequest role,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String clientId = keycloakService.getClientId(realm, client, token);
        if (clientId == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");

        boolean created = keycloakService.createRole(realm, clientId, role, token, client);
        if (!created) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create role");

        // Sync to Project Manager DB
        RealmProductRoleUrl pmRole = new RealmProductRoleUrl();
        pmRole.setRealmName(realm);
        pmRole.setProductName(client);
        pmRole.setRoleName(role.getName());
        pmRole.setUrl(role.getUrl() != null ? role.getUrl() : "");
        pmRole.setUri(role.getUri() != null ? role.getUri() : "");

        syncRolesToProjectManager(List.of(pmRole));

        return ResponseEntity.ok("Role created and synced successfully");
    }

    // ---------------- UPDATE ROLE ----------------
    @PutMapping("/roles/{realm}/{client}/{roleName}")
    public ResponseEntity<String> updateRole(
            @PathVariable String realm,
            @PathVariable String client,
            @PathVariable String roleName,
            @RequestBody RoleCreationRequest role,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String clientId = keycloakService.getClientId(realm, client, token);
        if (clientId == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");

        boolean updated = keycloakService.updateRole(realm, clientId, roleName, role, token, client);
        if (!updated) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to update role");

        // Sync updated role
        RealmProductRoleUrl pmRole = new RealmProductRoleUrl();
        pmRole.setRealmName(realm);
        pmRole.setProductName(client);
        pmRole.setRoleName(role.getName());
        pmRole.setUrl(role.getUrl() != null ? role.getUrl() : "");
        pmRole.setUri(role.getUri() != null ? role.getUri() : "");

        syncRolesToProjectManager(List.of(pmRole));

        return ResponseEntity.ok("Role updated and synced successfully");
    }

    // ---------------- DELETE ROLE ----------------
    @DeleteMapping("/roles/{realm}/{client}/{roleName}")
    public ResponseEntity<String> deleteRole(
            @PathVariable String realm,
            @PathVariable String client,
            @PathVariable String roleName,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String clientId = keycloakService.getClientId(realm, client, token);
        if (clientId == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");

        boolean deleted = keycloakService.deleteRole(realm, clientId, roleName, token);
        if (!deleted) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to delete role");

        try {
            String pmUrl = "http://localhost:8085/project/delete-role/" + realm + "/" + client + "/" + roleName;
            restTemplate.exchange(pmUrl, HttpMethod.DELETE, null, String.class);
        } catch (Exception e) {
            log.error("Failed deleting role from Project Manager: {}", e.getMessage());
        }

        return ResponseEntity.ok("Role deleted successfully");
    }

    // ---------------- HELPER ----------------
    private void syncRolesToProjectManager(List<RealmProductRoleUrl> roles) {
        try {
            String pmUrl = "http://localhost:8085/project/sync-roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(pmUrl, new HttpEntity<>(roles, headers), String.class);
            log.info("✅ Synced {} roles to Project Manager", roles.size());
        } catch (Exception e) {
            log.error("❌ Failed syncing roles: {}", e.getMessage(), e);
        }
    }
}
