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
}
