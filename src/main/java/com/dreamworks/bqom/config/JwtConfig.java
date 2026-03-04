package com.dreamworks.bqom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import org.springframework.web.client.RestTemplate;

@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.anon.key}") String anonKey) {

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("apikey", anonKey);
            return execution.execute(request, body);
        });

        return NimbusJwtDecoder.withJwkSetUri(supabaseUrl + "/auth/v1/.well-known/jwks.json")
                .restOperations(restTemplate)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }
}
