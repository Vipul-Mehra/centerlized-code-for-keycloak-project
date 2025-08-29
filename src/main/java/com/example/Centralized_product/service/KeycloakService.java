package com.example.Centralized_product.service;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Validate token using realm JWKS (public keys).
     */
    public Optional<String> validateAndGetUsername(String realm, String token) {
        try {
            // 1. Fetch JWKS (public keys) from Keycloak dynamically
            String jwksUrl = "http://localhost:8080/realms/" + realm + "/protocol/openid-connect/certs";
            String jwksJson = restTemplate.getForObject(jwksUrl, String.class);
            JWKSet jwkSet = JWKSet.parse(jwksJson);

            // 2. Parse the JWT
            JWSObject jwsObject = JWSObject.parse(token);

            // 3. Verify using JWKS
            for (JWK jwk : jwkSet.getKeys()) {
                if (jwsObject.verify(new RSASSAVerifier(jwk.toRSAKey()))) {
                    Map<String, Object> claims = jwsObject.getPayload().toJSONObject();
                    // Extract username claim
                    String username = (String) claims.getOrDefault("preferred_username", claims.get("sub"));
                    return Optional.ofNullable(username);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
