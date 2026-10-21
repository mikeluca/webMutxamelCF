package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.util.TokenUtils;

@ExtendWith(MockitoExtension.class)
class UsuarioAppServiceRecuperacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-25T10:00:00Z");

    private static final String MENSAJE = "Código incorrecto o caducado";

    @Mock
    private UsuarioAppDao usuarioAppDao;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    @Mock
    private RolAppDao rolAppDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private UsuarioAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsuarioAppServiceImpl(
                usuarioAppDao, usuarioAppVinculoDao, rolAppDao, passwordEncoder, jwtService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static UsuarioApp cuentaConCodigo(String codigo) {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(7);
        usuario.setEmail("ana@example.com");
        usuario.setActivo(true);
        usuario.setTokenRecuperacion(TokenUtils.hashToken(codigo));
        return usuario;
    }

    @Test
    void generarCodigoGuardaSoloElHashConCaducidadDe30MinutosYDevuelveElCodigo() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(7);
        usuario.setActivo(true);
        when(usuarioAppDao.obtenerPorId(7)).thenReturn(usuario);

        String codigo = service.generarCodigoRecuperacion(7);

        assertThat(codigo).matches("\\d{6}");
        ArgumentCaptor<Timestamp> expira = ArgumentCaptor.forClass(Timestamp.class);
        verify(usuarioAppDao).actualizarTokenRecuperacion(
                eq(7), eq(TokenUtils.hashToken(codigo)), expira.capture());
        assertThat(expira.getValue().toInstant()).isEqualTo(AHORA.plusSeconds(30 * 60));
    }

    @Test
    void generarCodigoRechazaCuentasInactivasOEliminadas() {
        UsuarioApp inactiva = new UsuarioApp();
        inactiva.setId(7);
        when(usuarioAppDao.obtenerPorId(7)).thenReturn(inactiva);

        assertThatThrownBy(() -> service.generarCodigoRecuperacion(7))
                .isInstanceOf(IllegalStateException.class);

        UsuarioApp eliminada = new UsuarioApp();
        eliminada.setId(8);
        eliminada.setActivo(true);
        eliminada.setFechaEliminacion(Timestamp.from(AHORA));
        when(usuarioAppDao.obtenerPorId(8)).thenReturn(eliminada);

        assertThatThrownBy(() -> service.generarCodigoRecuperacion(8))
                .isInstanceOf(IllegalStateException.class);

        verify(usuarioAppDao, never()).actualizarTokenRecuperacion(anyInt(), anyString(), any());
    }

    @Test
    void restablecerConCodigoCorrectoGuardaElNuevoHashYDevuelveLaCuenta() {
        UsuarioApp usuario = cuentaConCodigo("123456");
        when(usuarioAppDao.obtenerPorEmail("ana@example.com")).thenReturn(usuario);
        when(usuarioAppDao.consumirIntentoRecuperacion(eq(7), eq(5), eq(Timestamp.from(AHORA)))).thenReturn(1);
        when(passwordEncoder.encode("nueva-clave-1")).thenReturn("hash-nuevo");

        UsuarioApp resultado = service.restablecerPassword("Ana@Example.com", " 123456 ", "nueva-clave-1");

        assertThat(resultado).isSameAs(usuario);
        verify(usuarioAppDao).restablecerPassword(7, "hash-nuevo", Timestamp.from(AHORA));
        verify(usuarioAppDao, never()).invalidarRecuperacionSiAgotada(anyInt(), anyInt());
    }

    @Test
    void restablecerConCodigoIncorrectoGastaUnIntentoYDevuelveElMensajeGenerico() {
        when(usuarioAppDao.obtenerPorEmail("ana@example.com")).thenReturn(cuentaConCodigo("123456"));
        when(usuarioAppDao.consumirIntentoRecuperacion(eq(7), eq(5), any())).thenReturn(1);

        assertThatThrownBy(() -> service.restablecerPassword("ana@example.com", "000000", "nueva-clave-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE);

        verify(usuarioAppDao).consumirIntentoRecuperacion(eq(7), eq(5), any());
        // Tras gastar un intento se pide invalidar el código si ya van 5.
        verify(usuarioAppDao).invalidarRecuperacionSiAgotada(7, 5);
        verify(usuarioAppDao, never()).restablecerPassword(anyInt(), anyString(), any());
    }

    @Test
    void restablecerSinIntentosOCodigoCaducadoDaElMismoError() {
        // El UPDATE condicionado no afecta a ninguna fila: caducado o 5 intentos gastados.
        when(usuarioAppDao.obtenerPorEmail("ana@example.com")).thenReturn(cuentaConCodigo("123456"));
        when(usuarioAppDao.consumirIntentoRecuperacion(eq(7), eq(5), any())).thenReturn(0);

        assertThatThrownBy(() -> service.restablecerPassword("ana@example.com", "123456", "nueva-clave-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE);

        verify(usuarioAppDao, never()).restablecerPassword(anyInt(), anyString(), any());
    }

    @Test
    void restablecerSinCodigoVigenteOConCuentaNoActivaDaElMismoErrorSinGastarIntentos() {
        UsuarioApp sinCodigo = cuentaConCodigo("123456");
        sinCodigo.setTokenRecuperacion(null);
        when(usuarioAppDao.obtenerPorEmail("sin@example.com")).thenReturn(sinCodigo);

        UsuarioApp desactivada = cuentaConCodigo("123456");
        desactivada.setActivo(false);
        when(usuarioAppDao.obtenerPorEmail("off@example.com")).thenReturn(desactivada);

        when(usuarioAppDao.obtenerPorEmail("nadie@example.com")).thenReturn(null);

        for (String email : new String[] { "sin@example.com", "off@example.com", "nadie@example.com" }) {
            assertThatThrownBy(() -> service.restablecerPassword(email, "123456", "nueva-clave-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(MENSAJE);
        }

        verify(usuarioAppDao, never()).consumirIntentoRecuperacion(anyInt(), anyInt(), any());
    }

    @Test
    void restablecerRechazaUnaContrasenaInvalidaSinTocarLosIntentos() {
        assertThatThrownBy(() -> service.restablecerPassword("ana@example.com", "123456", "corta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña debe tener al menos 8 caracteres");

        verify(usuarioAppDao, never()).consumirIntentoRecuperacion(anyInt(), anyInt(), any());
        verify(usuarioAppDao, never()).restablecerPassword(anyInt(), anyString(), any());
    }
}
