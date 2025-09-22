package com.paxaris.ApiGateway.controller;

import com.paxaris.ApiGateway.dto.RoleCreationRequest;
import com.paxaris.ApiGateway.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectGatewayController {

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
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
        }

        boolean created = keycloakService.createRole(realm, clientId, role, token, client);
        if (!created) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create role");
        }

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
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
        }

        boolean updated = keycloakService.updateRole(realm, clientId, roleName, role, token, client);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to update role");
        }

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
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
        }

        boolean deleted = keycloakService.deleteRole(realm, clientId, roleName, token, client);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to delete role");
        }

        return ResponseEntity.ok("Role deleted successfully");
    }
}
