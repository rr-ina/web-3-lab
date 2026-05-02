package com.example.ssl;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Component
public class JwtDecoder {
    private static final Logger logger = LoggerFactory.getLogger(JwtDecoder.class);

    @Autowired
    private CasdoorProperties casdoor;

    public Claims decodeToken(String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String jwksUrl = casdoor.getEndpoint() + "/.well-known/jwks";
            String jwksJson = restTemplate.getForObject(jwksUrl, String.class);

            JWKSet jwkSet = JWKSet.parse(jwksJson);
            List<JWK> keys = jwkSet.getKeys();

            if (keys == null || keys.isEmpty()) {
                logger.error("No keys found in JWK Set from {}", jwksUrl);
                throw new RuntimeException("No keys found in JWK Set");
            }

            JWK jwk = keys.get(0);
            RSAPublicKey publicKey = (RSAPublicKey) jwk.toRSAKey().toPublicKey();

            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            } catch (Exception e) {
            logger.error("Failed to decode token", e);
            throw new RuntimeException("Failed to decode token", e);
        }
    }
}
