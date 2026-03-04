package com.dreamworks.bqom;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class JwtDecoderTest {
    public static void main(String[] args) {
        String token = "eyJhbGciOiJFUzI1NiIsImtpZCI6ImJhMGIxM2NhLTBkZDMtNDZiYy1hZDU3LWRkNjY1MDNlZWYxNSIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2tseXdreHJ3cWRtc3Vmc2dnc2tiLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiJlZjFhNDllZi1lMjkxLTRiM2UtOTBlNS1jZTAxYTNjNWE1OTYiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzcyNjA3MjUyLCJpYXQiOjE3NzI2MDM2NTIsImVtYWlsIjoiZGl2ZXN0aGlAZ21haWwuY29tIiwicGhvbmUiOiIiLCJhcHBfbWV0YWRhdGEiOnsicHJvdmlkZXIiOiJlbWFpbCIsInByb3ZpZGVycyI6WyJlbWFpbCJdfSwidXNlcl9tZXRhZGF0YSI6eyJlbWFpbF92ZXJpZmllZCI6dHJ1ZX0sInJvbGUiOiJhdXRoZW50aWNhdGVkIiwiYWFsIjoiYWFsMSIsImFtciI6W3sibWV0aG9kIjoicGFzc3dvcmQiLCJ0aW1lc3RhbXAiOjE3NzI2MDM2NTJ9XSwic2Vzc2lvbl9pZCI6ImEzOTA5NjljLTdmNWMtNDYxZi04N2VlLTI0ZmY2MDQwYTc3ZSIsImlzX2Fub255bW91cyI6ZmFsc2V9.1LZJfnWHEoMvpT662FLlTk4oVg875-X9MhiNL7S5hmlB9LViK8B768uQp5i5mQBjo8bO5WZpSJZLqbZlZM4hbg";
        String jwkSetUri = "https://klywkxrwdmsufsggskb.supabase.co/auth/v1/jwk";

        try {
            JwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            Jwt jwt = jwtDecoder.decode(token);
            System.out.println("Valid Token claims: " + jwt.getClaims());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
