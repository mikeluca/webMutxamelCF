package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;

@ExtendWith(MockitoExtension.class)
class UsuarioAppServiceImplTest {

    @Mock
    private UsuarioAppDao usuarioAppDao;

    @Mock
    private RolAppDao rolAppDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private UsuarioAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsuarioAppServiceImpl(usuarioAppDao, rolAppDao, passwordEncoder, jwtService);
    }

    private UsuarioApp usuarioActivo() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);
        usuario.setEmail("jugador@mutxamelcf.es");
        usuario.setPasswordHash("hash-almacenado");
        usuario.setActivo(true);
        return usuario;
    }

    @Test
    void loginConCredencialesValidasDevuelveTokenYRoles() {
        UsuarioApp usuario = usuarioActivo();
        RolApp rol = new RolApp();
        rol.setId(5);
        rol.setCodigo("JUGADOR");

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);
        when(passwordEncoder.matches("password123", "hash-almacenado")).thenReturn(true);
        when(rolAppDao.obtenerPorUsuario(1)).thenReturn(List.of(rol));
        when(jwtService.generarToken(eq(1), eq("jugador@mutxamelcf.es"), any())).thenReturn("jwt-generado");

        LoginAppResponse respuesta = service.login("jugador@mutxamelcf.es", "password123");

        assertThat(respuesta.getToken()).isEqualTo("jwt-generado");
        assertThat(respuesta.getUsuarioId()).isEqualTo(1);
        assertThat(respuesta.getRoles()).containsExactly("JUGADOR");
        verify(usuarioAppDao).actualizarUltimoAcceso(1);
    }

    @Test
    void loginConPasswordIncorrectaLanzaExcepcionYNoActualizaAcceso() {
        UsuarioApp usuario = usuarioActivo();

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);
        when(passwordEncoder.matches("incorrecta", "hash-almacenado")).thenReturn(false);

        assertThatThrownBy(() -> service.login("jugador@mutxamelcf.es", "incorrecta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incorrectos");

        verify(usuarioAppDao, never()).actualizarUltimoAcceso(anyInt());
    }

    @Test
    void loginConCuentaInactivaLanzaExcepcion() {
        UsuarioApp usuario = usuarioActivo();
        usuario.setActivo(false);

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);

        assertThatThrownBy(() -> service.login("jugador@mutxamelcf.es", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está activa");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginConEmailInexistenteLanzaExcepcionSinRevelarCual() {
        when(usuarioAppDao.obtenerPorEmail("desconocido@mutxamelcf.es")).thenReturn(null);

        assertThatThrownBy(() -> service.login("desconocido@mutxamelcf.es", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incorrectos");
    }

    @Test
    void activarCuentaConTokenValidoEstableceLaContrasenaYActiva() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(7);
        usuario.setActivo(false);

        when(usuarioAppDao.obtenerPorTokenActivacion(any())).thenReturn(usuario);
        when(passwordEncoder.encode("password123")).thenReturn("hash-nuevo");

        service.activarCuenta("token-valido", "password123");

        verify(usuarioAppDao).actualizarPassword(7, "hash-nuevo");
        verify(usuarioAppDao).activarUsuario(7);
    }

    @Test
    void activarCuentaConPasswordCortaLanzaExcepcionSinConsultarElToken() {
        assertThatThrownBy(() -> service.activarCuenta("token-valido", "corta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 caracteres");

        verify(usuarioAppDao, never()).obtenerPorTokenActivacion(any());
    }

    @Test
    void activarCuentaYaActivaLanzaExcepcion() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(7);
        usuario.setActivo(true);

        when(usuarioAppDao.obtenerPorTokenActivacion(any())).thenReturn(usuario);

        assertThatThrownBy(() -> service.activarCuenta("token-valido", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está activa");

        verify(usuarioAppDao, never()).actualizarPassword(anyInt(), any());
    }

    @Test
    void crearUsuarioConEmailYaExistenteLanzaExcepcionYNoGuarda() {
        UsuarioApp nuevo = new UsuarioApp();
        nuevo.setEmail("Repetido@Mutxamelcf.es");

        when(usuarioAppDao.obtenerPorEmail("repetido@mutxamelcf.es")).thenReturn(new UsuarioApp());

        assertThatThrownBy(() -> service.crearUsuario(nuevo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe");

        verify(usuarioAppDao, never()).guardar(any());
    }

    @Test
    void crearUsuarioNormalizaElEmailYQuedaInactivoSinPassword() {
        UsuarioApp nuevo = new UsuarioApp();
        nuevo.setEmail("Nuevo@Mutxamelcf.es");
        nuevo.setPasswordHash("no-deberia-persistir");

        when(usuarioAppDao.obtenerPorEmail("nuevo@mutxamelcf.es")).thenReturn(null);
        when(usuarioAppDao.guardar(any())).thenReturn(9);

        int id = service.crearUsuario(nuevo);

        assertThat(id).isEqualTo(9);
        assertThat(nuevo.getEmail()).isEqualTo("nuevo@mutxamelcf.es");
        assertThat(nuevo.isActivo()).isFalse();
        assertThat(nuevo.getPasswordHash()).isNull();
        verify(usuarioAppDao, times(1)).guardar(nuevo);
    }

    @Test
    void asignarRolNoDuplicaSiElUsuarioYaLoTiene() {
        RolApp rol = new RolApp();
        rol.setId(3);
        rol.setCodigo("ENTRENADOR");

        when(rolAppDao.obtenerPorUsuario(1)).thenReturn(List.of(rol));

        service.asignarRol(1, 3);

        verify(rolAppDao, never()).asignarRol(anyInt(), anyInt());
    }
}
