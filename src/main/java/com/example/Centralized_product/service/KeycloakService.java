package com.example.Centralized_product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

@Service
public class KeycloakService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Validate token using realm JWKS (no need clientId/clientSecret again)
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

                    // Preferred claim for username
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
