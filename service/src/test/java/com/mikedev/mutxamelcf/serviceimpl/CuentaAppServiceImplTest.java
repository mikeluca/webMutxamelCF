package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.dao.NotificacionAppDao;
import com.mikedev.mutxamelcf.dao.PreferenciasNotificacionDao;
import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.UsuarioApp;

@ExtendWith(MockitoExtension.class)
class CuentaAppServiceImplTest {

    @Mock
    private UsuarioAppDao usuarioAppDao;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    @Mock
    private RolAppDao rolAppDao;

    @Mock
    private DispositivoAppDao dispositivoAppDao;

    @Mock
    private NotificacionAppDao notificacionAppDao;

    @Mock
    private PreferenciasNotificacionDao preferenciasNotificacionDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CuentaAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CuentaAppServiceImpl(
                usuarioAppDao, usuarioAppVinculoDao, rolAppDao, dispositivoAppDao,
                notificacionAppDao, preferenciasNotificacionDao, passwordEncoder);
    }

    private UsuarioApp usuarioActivo() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(5);
        usuario.setEmail("jugador@mutxamelcf.es");
        usuario.setPasswordHash("hash");
        usuario.setActivo(true);
        return usuario;
    }

    @Test
    void eliminarCuentaBorraDatosPersonalesYAnonimizaLaFila() {
        when(usuarioAppDao.obtenerPorId(5)).thenReturn(usuarioActivo());
        when(passwordEncoder.matches("secreta123", "hash")).thenReturn(true);

        service.eliminarCuenta(5, "secreta123");

        InOrder orden = inOrder(
                dispositivoAppDao, notificacionAppDao, preferenciasNotificacionDao,
                usuarioAppVinculoDao, rolAppDao, usuarioAppDao);
        orden.verify(dispositivoAppDao).eliminarTodosDeUsuario(5L);
        orden.verify(notificacionAppDao).eliminarTodasDeUsuario(5L);
        orden.verify(preferenciasNotificacionDao).eliminarPorUsuario(5L);
        orden.verify(usuarioAppVinculoDao).desvincularTodo(5);
        orden.verify(rolAppDao).eliminarTodosLosRoles(5);
        orden.verify(usuarioAppDao).anonimizar(5, "eliminada-5@cuenta-eliminada.invalid");
        verify(usuarioAppDao, never()).eliminar(anyInt());
    }

    @Test
    void eliminarCuentaConPasswordIncorrectaNoBorraNada() {
        when(usuarioAppDao.obtenerPorId(5)).thenReturn(usuarioActivo());
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.eliminarCuenta(5, "mala"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña no es correcta");

        verify(usuarioAppDao, never()).anonimizar(anyInt(), anyString());
        verify(usuarioAppVinculoDao, never()).desvincularTodo(anyInt());
    }

    @Test
    void eliminarCuentaConPasswordVaciaLanzaExcepcion() {
        when(usuarioAppDao.obtenerPorId(5)).thenReturn(usuarioActivo());

        assertThatThrownBy(() -> service.eliminarCuenta(5, " "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppDao, never()).anonimizar(anyInt(), anyString());
    }

    @Test
    void eliminarCuentaDeUsuarioInexistenteLanzaExcepcion() {
        when(usuarioAppDao.obtenerPorId(9)).thenReturn(null);

        assertThatThrownBy(() -> service.eliminarCuenta(9, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario no existe");
    }

    @Test
    void eliminarCuentaYaEliminadaLanzaExcepcion() {
        UsuarioApp eliminada = usuarioActivo();
        eliminada.setFechaEliminacion(new Timestamp(System.currentTimeMillis()));
        when(usuarioAppDao.obtenerPorId(5)).thenReturn(eliminada);

        assertThatThrownBy(() -> service.eliminarCuenta(5, "secreta123"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppDao, never()).anonimizar(anyInt(), anyString());
    }
}
