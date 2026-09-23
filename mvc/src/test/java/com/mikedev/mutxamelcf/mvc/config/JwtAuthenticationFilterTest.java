package com.mikedev.mutxamelcf.mvc.config;

import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UsuarioAppService usuarioAppService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        usuarioAppService = mock(UsuarioAppService.class);
        filter = new JwtAuthenticationFilter(jwtService, usuarioAppService);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilterEsFalseParaRutasDeLaApi() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilterEsTrueParaRutasFueraDeLaApi() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pagos");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void sinCabeceraAuthorizationContinuaSinAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void cabeceraSinBearerContinuaSinAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Basic algo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenInvalidoContinuaSinAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer token-invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.esValido("token-invalido")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void usuarioInexistenteLimpiaElContextoYContinua() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.esValido("token-valido")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-valido")).thenReturn(1);
        when(usuarioAppService.obtenerPorId(1)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void usuarioInactivoLimpiaElContextoYContinua() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.esValido("token-valido")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-valido")).thenReturn(1);
        UsuarioApp usuario = new UsuarioApp();
        usuario.setActivo(false);
        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenValidoAutenticaAlUsuarioConSusRoles() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.esValido("token-valido")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-valido")).thenReturn(1);
        UsuarioApp usuario = new UsuarioApp();
        usuario.setActivo(true);
        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        RolApp rol = new RolApp();
        rol.setCodigo("JUGADOR");
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of(rol));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_JUGADOR");
    }

    @Test
    void noSustituyeUnaAutenticacionYaExistenteEnElContexto() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.esValido("token-valido")).thenReturn(true);

        UsernamePasswordAuthenticationToken existente = new UsernamePasswordAuthenticationToken("ya-autenticado",
                null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existente);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existente);
        verify(usuarioAppService, never()).obtenerPorId(any(Integer.class));
    }

    @Test
    void unaExcepcionAlProcesarElTokenLimpiaElContextoYContinua() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/auth/me");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.esValido("token-valido")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-valido")).thenThrow(new RuntimeException("token corrupto"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
