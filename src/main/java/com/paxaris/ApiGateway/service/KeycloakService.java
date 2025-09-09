package com.paxaris.ApiGateway.service;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();

    public Optional<String> validateAndGetUsername(String realm, String token) {
        try {
            String jwksUrl = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/certs";
            String jwksJson = restTemplate.getForObject(jwksUrl, String.class);
            JWKSet jwkSet = JWKSet.parse(jwksJson);

            JWSObject jwsObject = JWSObject.parse(token);

            for (JWK jwk : jwkSet.getKeys()) {
                if (jwsObject.verify(new RSASSAVerifier(jwk.toRSAKey()))) {
                    Map<String, Object> claims = jwsObject.getPayload().toJSONObject();
                    return Optional.ofNullable((String) claims.getOrDefault("preferred_username", claims.get("sub")));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
    // 🔹 NEW METHOD: extract role from JWT
    @SuppressWarnings("unchecked")
    public List<String> getRoleFromToken(String token) {
        try {
            JWSObject jwsObject = JWSObject.parse(token);
            Map<String, Object> claims = jwsObject.getPayload().toJSONObject();
            List<String> roleName = new ArrayList<>();

            // Realm roles
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> realmRoles) {
                roleName.addAll(realmRoles.stream().map(Object::toString).toList());
            }

            // Client roles
            Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
            if (resourceAccess != null) {
                for (Object clientEntry : resourceAccess.values()) {
                    if (clientEntry instanceof Map<?, ?> clientMap) {
                        Object clientRolesObj = clientMap.get("roles");
                        if (clientRolesObj instanceof Collection<?> clientRoles) {
                            roleName.addAll(clientRoles.stream().map(Object::toString).toList());
                        }
                    }
                }
            }

            return roleName;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


}
