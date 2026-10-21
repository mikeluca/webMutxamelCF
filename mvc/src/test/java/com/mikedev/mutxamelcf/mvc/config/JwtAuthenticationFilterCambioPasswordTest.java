package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.servlet.FilterChain;

/**
 * Los JWT emitidos antes de un cambio de contraseña dejan de valer. Usa un JwtService
 * real para comprobar el iat de verdad (en segundos).
 */
class JwtAuthenticationFilterCambioPasswordTest {

    private static final String SECRETO = "ClaveDePruebasSoloParaTestsDeIntegracion123456";

    private final JwtService jwtService = new JwtService(SECRETO, 3_600_000L);
    private UsuarioAppService usuarioAppService;
    private JwtAuthenticationFilter filter;
    private UsuarioApp usuario;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        filter = new JwtAuthenticationFilter(jwtService, usuarioAppService);

        usuario = new UsuarioApp();
        usuario.setId(1);
        usuario.setEmail("ana@example.com");
        usuario.setActivo(true);
        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of());
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private boolean autenticado(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private String tokenEmitidoAhora() {
        return jwtService.generarToken(1, "ana@example.com", List.<RolApp>of());
    }

    @Test
    void sinCambioDePasswordElTokenSiguesiendoValido() throws Exception {
        usuario.setFechaCambioPassword(null);

        assertThat(autenticado(tokenEmitidoAhora())).isTrue();
    }

    @Test
    void elTokenEmitidoAntesDelCambioNoAutentica() throws Exception {
        String viejo = tokenEmitidoAhora();
        usuario.setFechaCambioPassword(Timestamp.from(Instant.now().plus(5, ChronoUnit.SECONDS)));

        assertThat(autenticado(viejo)).isFalse();
    }

    @Test
    void elTokenQueDevuelveRestablecerPasaElFiltro() throws Exception {
        // Restablecer fija FECHA_CAMBIO_PASSWORD (con milisegundos) y después hace login:
        // el token nuevo se emite en el mismo segundo o después.
        usuario.setFechaCambioPassword(Timestamp.from(Instant.now()));

        assertThat(autenticado(tokenEmitidoAhora())).isTrue();
    }

    @Test
    void unTokenDeAntesDelCambioNoPasaPeroUnoPosteriorSi() throws Exception {
        String viejo = tokenEmitidoAhora();
        usuario.setFechaCambioPassword(Timestamp.from(Instant.now().plus(2, ChronoUnit.SECONDS)));

        assertThat(autenticado(viejo)).isFalse();

        usuario.setFechaCambioPassword(Timestamp.from(Instant.now().minus(10, ChronoUnit.SECONDS)));
        assertThat(autenticado(viejo)).isTrue();
    }
}
