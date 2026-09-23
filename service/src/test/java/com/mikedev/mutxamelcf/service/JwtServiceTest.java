package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.RolApp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRETO = "ClaveDePruebasSoloParaTestsDeUnidad1234567890";

    private static RolApp rol(String codigo) {
        RolApp rol = new RolApp();
        rol.setCodigo(codigo);
        return rol;
    }

    @Test
    void generarTokenIncluyeSubjectEmailYRoles() {
        JwtService service = new JwtService(SECRETO, 60_000);

        String token = service.generarToken(1, "juan@example.com", List.of(rol("JUGADOR"), rol("FAMILIAR")));

        Claims claims = service.extraerClaims(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email")).isEqualTo("juan@example.com");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertThat(roles).containsExactly("JUGADOR", "FAMILIAR");
    }

    @Test
    void extraerUsuarioIdDevuelveElIdDelToken() {
        JwtService service = new JwtService(SECRETO, 60_000);

        String token = service.generarToken(42, "juan@example.com", List.of());

        assertThat(service.extraerUsuarioId(token)).isEqualTo(42);
    }

    @Test
    void esValidoDevuelveTrueParaUnTokenReciente() {
        JwtService service = new JwtService(SECRETO, 60_000);

        String token = service.generarToken(1, "juan@example.com", List.of());

        assertThat(service.esValido(token)).isTrue();
    }

    @Test
    void esValidoDevuelveFalseParaUnTokenCaducado() {
        JwtService service = new JwtService(SECRETO, -1_000);

        String token = service.generarToken(1, "juan@example.com", List.of());

        assertThat(service.esValido(token)).isFalse();
    }

    @Test
    void esValidoDevuelveFalseParaUnTokenConFirmaInvalida() {
        JwtService service = new JwtService(SECRETO, 60_000);

        SecretKey otraClave = Keys.hmacShaKeyFor(
                "OtraClaveQueNoEsLaDelServicioDePruebas12345".getBytes(StandardCharsets.UTF_8));

        String tokenManipulado = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otraClave)
                .compact();

        assertThat(service.esValido(tokenManipulado)).isFalse();
    }

    @Test
    void esValidoDevuelveFalseParaUnTokenMalFormado() {
        JwtService service = new JwtService(SECRETO, 60_000);

        assertThat(service.esValido("esto-no-es-un-jwt")).isFalse();
    }

    @Test
    void extraerClaimsLanzaExcepcionParaUnTokenCaducado() {
        JwtService service = new JwtService(SECRETO, -1_000);

        String token = service.generarToken(1, "juan@example.com", List.of());

        assertThatThrownBy(() -> service.extraerClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }
}
