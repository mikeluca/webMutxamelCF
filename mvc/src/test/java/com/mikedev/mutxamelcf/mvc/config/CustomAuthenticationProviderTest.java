package com.mikedev.mutxamelcf.mvc.config;

import com.mikedev.mutxamelcf.model.UsuarioDTO;
import com.mikedev.mutxamelcf.service.UsuarioService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomAuthenticationProviderTest {

    private UsuarioService userService;
    private LoginRateLimiter rateLimiter;
    private CustomAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        userService = mock(UsuarioService.class);
        rateLimiter = mock(LoginRateLimiter.class);
        provider = new CustomAuthenticationProvider(userService, rateLimiter);
        when(rateLimiter.clave(null, "admin")).thenReturn("clave");
    }

    private static UsernamePasswordAuthenticationToken peticion(String usuario, String password) {
        return new UsernamePasswordAuthenticationToken(usuario, password);
    }

    @Test
    void authenticateLanzaLockedExceptionSiEstaBloqueado() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(true);

        assertThatThrownBy(() -> provider.authenticate(peticion("admin", "1234")))
                .isInstanceOf(LockedException.class);
        verify(userService, org.mockito.Mockito.never()).validarUsuario(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticateLanzaBadCredentialsYRegistraFalloSiElUsuarioNoExiste() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(false);
        when(userService.validarUsuario("admin", "1234")).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(peticion("admin", "1234")))
                .isInstanceOf(BadCredentialsException.class);
        verify(rateLimiter).registrarFallo("clave");
    }

    @Test
    void authenticateDevuelveTokenConRolYRegistraExito() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(false);
        UsuarioDTO usuario = new UsuarioDTO();
        usuario.setUsuario("admin");
        usuario.setRol(" super ");
        when(userService.validarUsuario("admin", "1234")).thenReturn(usuario);

        Authentication resultado = provider.authenticate(peticion("admin", "1234"));

        assertThat(resultado.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SUPER");
        verify(rateLimiter).registrarExito("clave");
    }

    @Test
    void authenticateDevuelveSinAuthoritiesSiElRolEsNulo() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(false);
        UsuarioDTO usuario = new UsuarioDTO();
        usuario.setUsuario("admin");
        usuario.setRol(null);
        when(userService.validarUsuario("admin", "1234")).thenReturn(usuario);

        Authentication resultado = provider.authenticate(peticion("admin", "1234"));

        assertThat(resultado.getAuthorities()).isEmpty();
    }

    @Test
    void authenticateUsaLaIpDeLosDetallesWebParaLaClave() {
        UsernamePasswordAuthenticationToken peticion = peticion("admin", "1234");
        WebAuthenticationDetails detalles = mock(WebAuthenticationDetails.class);
        when(detalles.getRemoteAddress()).thenReturn("10.0.0.5");
        peticion.setDetails(detalles);
        when(rateLimiter.clave("10.0.0.5", "admin")).thenReturn("clave-ip");
        when(rateLimiter.estaBloqueado("clave-ip")).thenReturn(false);
        when(userService.validarUsuario("admin", "1234")).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(peticion)).isInstanceOf(BadCredentialsException.class);
        verify(rateLimiter).registrarFallo("clave-ip");
    }

    @Test
    void supportsAceptaUsernamePasswordAuthenticationToken() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }
}
