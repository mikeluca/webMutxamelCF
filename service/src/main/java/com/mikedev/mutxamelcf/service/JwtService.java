package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.RolApp;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMillis) {

        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));

        this.expirationMillis = expirationMillis;
    }

    /**
     * Genera el JWT del usuario.
     */
    public String generarToken(
            int usuarioId,
            String email,
            List<RolApp> roles) {

        List<String> codigosRoles = roles.stream()
                .map(RolApp::getCodigo)
                .toList();

        Date ahora = new Date();
        Date expiracion = new Date(
                ahora.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("email", email)
                .claim("roles", codigosRoles)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extrae todos los claims del token.
     */
    public Claims extraerClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Devuelve el ID del usuario almacenado en el JWT.
     */
    public int extraerUsuarioId(String token) {

        return Integer.parseInt(
                extraerClaims(token).getSubject());
    }

    /**
     * Comprueba que el token sea válido y no esté caducado.
     */
    public boolean esValido(String token) {

        try {

            Claims claims = extraerClaims(token);

            return claims.getExpiration().after(new Date());

        } catch (Exception e) {

            return false;
        }
    }
}