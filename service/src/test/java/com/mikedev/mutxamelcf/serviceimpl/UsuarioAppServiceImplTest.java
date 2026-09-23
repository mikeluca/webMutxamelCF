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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.util.TokenUtils;

@ExtendWith(MockitoExtension.class)
class UsuarioAppServiceImplTest {

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
                usuarioAppDao, usuarioAppVinculoDao, rolAppDao, passwordEncoder, jwtService);
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

    private UsuarioApp usuarioPendienteActivacion(String codigo) {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(7);
        usuario.setEmail("jugador@mutxamelcf.es");
        usuario.setActivo(false);
        usuario.setTokenActivacion(TokenUtils.hashToken(codigo));
        usuario.setFechaExpiracionToken(
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));
        usuario.setIntentosActivacion(0);
        return usuario;
    }

    @Test
    void activarCuentaConCodigoValidoEstableceLaContrasenaYActivaYDevuelveElUsuario() {
        UsuarioApp usuario = usuarioPendienteActivacion("123456");

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);
        when(passwordEncoder.encode("password123")).thenReturn("hash-nuevo");

        UsuarioApp resultado = service.activarCuenta(
                "jugador@mutxamelcf.es", "123456", "password123");

        assertThat(resultado.getId()).isEqualTo(7);
        assertThat(resultado.getEmail()).isEqualTo("jugador@mutxamelcf.es");
        verify(usuarioAppDao).actualizarPassword(7, "hash-nuevo");
        verify(usuarioAppDao).activarUsuario(7);
    }

    @Test
    void activarCuentaConPasswordCortaLanzaExcepcionSinConsultarElEmail() {
        assertThatThrownBy(() -> service.activarCuenta(
                        "jugador@mutxamelcf.es", "123456", "corta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 caracteres");

        verify(usuarioAppDao, never()).obtenerPorEmail(any());
    }

    @Test
    void activarCuentaYaActivaLanzaExcepcion() {
        UsuarioApp usuario = usuarioPendienteActivacion("123456");
        usuario.setActivo(true);

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);

        assertThatThrownBy(() -> service.activarCuenta(
                        "jugador@mutxamelcf.es", "123456", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está activa");

        verify(usuarioAppDao, never()).actualizarPassword(anyInt(), any());
    }

    @Test
    void activarCuentaConEmailDesconocidoLanzaExcepcion() {
        when(usuarioAppDao.obtenerPorEmail("desconocido@mutxamelcf.es")).thenReturn(null);

        assertThatThrownBy(() -> service.activarCuenta(
                        "desconocido@mutxamelcf.es", "123456", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es válido");
    }

    @Test
    void activarCuentaConCodigoCaducadoLanzaExcepcion() {
        UsuarioApp usuario = usuarioPendienteActivacion("123456");
        usuario.setFechaExpiracionToken(
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);

        assertThatThrownBy(() -> service.activarCuenta(
                        "jugador@mutxamelcf.es", "123456", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caducado");

        verify(usuarioAppDao, never()).actualizarPassword(anyInt(), any());
    }

    @Test
    void activarCuentaConCodigoIncorrectoIncrementaIntentosYAvisaCuantosQuedan() {
        UsuarioApp usuario = usuarioPendienteActivacion("123456");
        usuario.setIntentosActivacion(1);

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);

        assertThatThrownBy(() -> service.activarCuenta(
                        "jugador@mutxamelcf.es", "000000", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Código incorrecto")
                .hasMessageContaining("3");

        verify(usuarioAppDao).incrementarIntentosActivacion(7);
        verify(usuarioAppDao, never()).invalidarTokenActivacion(anyInt());
        verify(usuarioAppDao, never()).actualizarPassword(anyInt(), any());
    }

    @Test
    void activarCuentaConUltimoIntentoFallidoInvalidaElCodigo() {
        UsuarioApp usuario = usuarioPendienteActivacion("123456");
        usuario.setIntentosActivacion(4);

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);

        assertThatThrownBy(() -> service.activarCuenta(
                        "jugador@mutxamelcf.es", "000000", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agotado");

        verify(usuarioAppDao).incrementarIntentosActivacion(7);
        verify(usuarioAppDao).invalidarTokenActivacion(7);
    }

    @Test
    void activarCuentaConIntentosYaAgotadosLanzaExcepcionSinComprobarElCodigo() {
        UsuarioApp usuario = usuarioPendienteActivacion("123456");
        usuario.setIntentosActivacion(5);

        when(usuarioAppDao.obtenerPorEmail("jugador@mutxamelcf.es")).thenReturn(usuario);

        assertThatThrownBy(() -> service.activarCuenta(
                        "jugador@mutxamelcf.es", "123456", "password123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agotado");

        verify(usuarioAppDao, never()).incrementarIntentosActivacion(anyInt());
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

    private InvitarUsuarioAppRequest requestInvitacion(String tipo, Long personaId, String email) {
        InvitarUsuarioAppRequest request = new InvitarUsuarioAppRequest();
        request.setVinculos(List.of(new VinculoSolicitado(tipo, personaId)));
        request.setEmail(email);
        return request;
    }

    private InvitarUsuarioAppRequest requestInvitacion(List<VinculoSolicitado> vinculos, String email) {
        InvitarUsuarioAppRequest request = new InvitarUsuarioAppRequest();
        request.setVinculos(vinculos);
        request.setEmail(email);
        return request;
    }

    private RolApp rol(int id, String codigo) {
        RolApp rol = new RolApp();
        rol.setId(id);
        rol.setCodigo(codigo);
        return rol;
    }

    @Test
    void invitarUsuarioConJugadorValidoLoCreaLoVinculaYGeneraToken() {
        InvitarUsuarioAppRequest request = requestInvitacion("JUGADOR", 55L, "Nuevo@Mutxamelcf.es");

        when(usuarioAppVinculoDao.jugadorTieneCuenta(55L)).thenReturn(false);
        when(usuarioAppVinculoDao.obtenerNombrePersona("JUGADOR", 55L)).thenReturn("Juan Perez");
        when(rolAppDao.obtenerPorCodigo("JUGADOR")).thenReturn(rol(2, "JUGADOR"));
        when(usuarioAppDao.obtenerPorEmail("nuevo@mutxamelcf.es")).thenReturn(null);
        when(usuarioAppDao.guardar(any())).thenReturn(42);

        UsuarioApp creado = new UsuarioApp();
        creado.setId(42);
        creado.setActivo(false);
        when(usuarioAppDao.obtenerPorId(42)).thenReturn(creado);
        when(rolAppDao.obtenerPorUsuario(42)).thenReturn(List.of());

        InvitacionUsuarioApp invitacion = service.invitarUsuario(request);

        assertThat(invitacion.getUsuarioAppId()).isEqualTo(42);
        assertThat(invitacion.getEmail()).isEqualTo("nuevo@mutxamelcf.es");
        assertThat(invitacion.getNombrePersona()).isEqualTo("Juan Perez");
        assertThat(invitacion.getTokenActivacion()).isNotBlank();

        verify(usuarioAppVinculoDao).vincularJugador(42, 55L);
        verify(rolAppDao).asignarRol(42, 2);
        verify(usuarioAppDao).actualizarTokenActivacion(eq(42), any(), any());
    }

    @Test
    void invitarUsuarioFamiliarUsaElEmailDeLaFichaIgnorandoElDeLaPeticion() {
        InvitarUsuarioAppRequest request = requestInvitacion("FAMILIAR", 8L, "email-manipulado@otrodominio.com");

        when(usuarioAppVinculoDao.familiarTieneCuenta(8L)).thenReturn(false);
        when(usuarioAppVinculoDao.obtenerNombrePersona("FAMILIAR", 8L)).thenReturn("Maria Garcia");
        when(usuarioAppVinculoDao.obtenerEmailFamiliar(8L)).thenReturn("Maria@Mutxamelcf.es");
        when(rolAppDao.obtenerPorCodigo("FAMILIAR")).thenReturn(rol(4, "FAMILIAR"));
        when(usuarioAppDao.obtenerPorEmail("maria@mutxamelcf.es")).thenReturn(null);
        when(usuarioAppDao.guardar(any())).thenReturn(15);

        UsuarioApp creado = new UsuarioApp();
        creado.setId(15);
        creado.setActivo(false);
        when(usuarioAppDao.obtenerPorId(15)).thenReturn(creado);
        when(rolAppDao.obtenerPorUsuario(15)).thenReturn(List.of());

        InvitacionUsuarioApp invitacion = service.invitarUsuario(request);

        assertThat(invitacion.getEmail()).isEqualTo("maria@mutxamelcf.es");
        verify(usuarioAppVinculoDao).vincularFamiliar(15, 8L);
    }

    @Test
    void invitarUsuarioFamiliarSinEmailRegistradoLanzaExcepcionYNoCreaNada() {
        InvitarUsuarioAppRequest request = requestInvitacion("FAMILIAR", 8L, "intento@mutxamelcf.es");

        when(usuarioAppVinculoDao.familiarTieneCuenta(8L)).thenReturn(false);
        when(usuarioAppVinculoDao.obtenerNombrePersona("FAMILIAR", 8L)).thenReturn("Maria Garcia");
        when(usuarioAppVinculoDao.obtenerEmailFamiliar(8L)).thenReturn(null);
        when(rolAppDao.obtenerPorCodigo("FAMILIAR")).thenReturn(rol(4, "FAMILIAR"));

        assertThatThrownBy(() -> service.invitarUsuario(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene un email registrado");

        verify(usuarioAppDao, never()).guardar(any());
    }

    @Test
    void invitarUsuarioConPersonaQueYaTieneCuentaLanzaExcepcionYNoCreaNada() {
        InvitarUsuarioAppRequest request = requestInvitacion("FAMILIAR", 8L, "familiar@mutxamelcf.es");

        when(usuarioAppVinculoDao.familiarTieneCuenta(8L)).thenReturn(true);

        assertThatThrownBy(() -> service.invitarUsuario(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya tiene una cuenta");

        verify(usuarioAppDao, never()).guardar(any());
        verify(usuarioAppVinculoDao, never()).vincularFamiliar(anyInt(), any());
    }

    @Test
    void invitarUsuarioConRolNoConfiguradoLanzaIllegalStateYNoCreaNada() {
        InvitarUsuarioAppRequest request = requestInvitacion("COORDINADOR", null, "coordinador@mutxamelcf.es");

        when(rolAppDao.obtenerPorCodigo("COORDINADOR")).thenReturn(null);

        assertThatThrownBy(() -> service.invitarUsuario(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COORDINADOR");

        verify(usuarioAppDao, never()).guardar(any());
    }

    @Test
    void invitarUsuarioCoordinadorNoRequierePersonaVinculada() {
        InvitarUsuarioAppRequest request = requestInvitacion("COORDINADOR", null, "coordinador@mutxamelcf.es");

        when(rolAppDao.obtenerPorCodigo("COORDINADOR")).thenReturn(rol(9, "COORDINADOR"));
        when(usuarioAppDao.obtenerPorEmail("coordinador@mutxamelcf.es")).thenReturn(null);
        when(usuarioAppDao.guardar(any())).thenReturn(7);

        UsuarioApp creado = new UsuarioApp();
        creado.setId(7);
        creado.setActivo(false);
        when(usuarioAppDao.obtenerPorId(7)).thenReturn(creado);
        when(rolAppDao.obtenerPorUsuario(7)).thenReturn(List.of());

        InvitacionUsuarioApp invitacion = service.invitarUsuario(request);

        assertThat(invitacion.getNombrePersona()).isNull();
        verify(rolAppDao).asignarRol(7, 9);
        verify(usuarioAppVinculoDao, never()).vincularJugador(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).vincularFamiliar(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).vincularCuerpoTecnico(anyInt(), any());
    }

    @Test
    void invitarUsuarioRetransmisionNoRequierePersonaVinculada() {
        InvitarUsuarioAppRequest request = requestInvitacion("RETRANSMISION", null, "retransmision@mutxamelcf.es");

        when(rolAppDao.obtenerPorCodigo("RETRANSMISION")).thenReturn(rol(10, "RETRANSMISION"));
        when(usuarioAppDao.obtenerPorEmail("retransmision@mutxamelcf.es")).thenReturn(null);
        when(usuarioAppDao.guardar(any())).thenReturn(8);

        UsuarioApp creado = new UsuarioApp();
        creado.setId(8);
        creado.setActivo(false);
        when(usuarioAppDao.obtenerPorId(8)).thenReturn(creado);
        when(rolAppDao.obtenerPorUsuario(8)).thenReturn(List.of());

        InvitacionUsuarioApp invitacion = service.invitarUsuario(request);

        assertThat(invitacion.getNombrePersona()).isNull();
        verify(rolAppDao).asignarRol(8, 10);
        verify(usuarioAppVinculoDao, never()).vincularJugador(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).vincularFamiliar(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).vincularCuerpoTecnico(anyInt(), any());
    }

    @Test
    void invitarUsuarioConVariosVinculosALaVezLosAplicaTodos() {
        InvitarUsuarioAppRequest request = requestInvitacion(
                List.of(
                        new VinculoSolicitado("JUGADOR", 55L),
                        new VinculoSolicitado("ENTRENADOR", 9L)),
                "Nuevo@Mutxamelcf.es");

        when(usuarioAppVinculoDao.jugadorTieneCuenta(55L)).thenReturn(false);
        when(usuarioAppVinculoDao.obtenerNombrePersona("JUGADOR", 55L)).thenReturn("Juan Perez");
        when(usuarioAppVinculoDao.cuerpoTecnicoTieneCuenta(9L)).thenReturn(false);
        when(usuarioAppVinculoDao.obtenerNombrePersona("ENTRENADOR", 9L)).thenReturn("Carlos Tecnico");
        when(rolAppDao.obtenerPorCodigo("JUGADOR")).thenReturn(rol(2, "JUGADOR"));
        when(rolAppDao.obtenerPorCodigo("ENTRENADOR")).thenReturn(rol(6, "ENTRENADOR"));
        when(usuarioAppDao.obtenerPorEmail("nuevo@mutxamelcf.es")).thenReturn(null);
        when(usuarioAppDao.guardar(any())).thenReturn(50);

        UsuarioApp creado = new UsuarioApp();
        creado.setId(50);
        creado.setActivo(false);
        when(usuarioAppDao.obtenerPorId(50)).thenReturn(creado);
        when(rolAppDao.obtenerPorUsuario(50)).thenReturn(List.of());

        InvitacionUsuarioApp invitacion = service.invitarUsuario(request);

        assertThat(invitacion.getUsuarioAppId()).isEqualTo(50);
        assertThat(invitacion.getNombrePersona()).isEqualTo("Juan Perez");

        verify(usuarioAppVinculoDao).vincularJugador(50, 55L);
        verify(usuarioAppVinculoDao).vincularCuerpoTecnico(50, 9L);
        verify(rolAppDao).asignarRol(50, 2);
        verify(rolAppDao).asignarRol(50, 6);
    }

    @Test
    void agregarVinculosAUsuarioExistenteAnadeVinculoNuevoAUnaCuentaYaCreada() {
        UsuarioApp existente = new UsuarioApp();
        existente.setId(60);
        existente.setActivo(true);

        when(usuarioAppDao.obtenerPorId(60)).thenReturn(existente);
        when(usuarioAppVinculoDao.cuerpoTecnicoTieneCuenta(11L)).thenReturn(false);
        when(usuarioAppVinculoDao.obtenerNombrePersona("ENTRENADOR", 11L)).thenReturn("Nuevo Tecnico");
        when(rolAppDao.obtenerPorCodigo("ENTRENADOR")).thenReturn(rol(6, "ENTRENADOR"));
        when(rolAppDao.obtenerPorUsuario(60)).thenReturn(List.of());

        AnadirVinculosRequest request = new AnadirVinculosRequest();
        request.setVinculos(List.of(new VinculoSolicitado("ENTRENADOR", 11L)));

        service.agregarVinculosAUsuarioExistente(60, request);

        verify(usuarioAppVinculoDao).vincularCuerpoTecnico(60, 11L);
        verify(rolAppDao).asignarRol(60, 6);
    }

    @Test
    void agregarVinculosAUsuarioInexistenteLanzaExcepcionYNoVinculaNada() {
        when(usuarioAppDao.obtenerPorId(70)).thenReturn(null);

        AnadirVinculosRequest request = new AnadirVinculosRequest();
        request.setVinculos(List.of(new VinculoSolicitado("JUGADOR", 1L)));

        assertThatThrownBy(() -> service.agregarVinculosAUsuarioExistente(70, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppVinculoDao, never()).vincularJugador(anyInt(), any());
    }

    @Test
    void quitarVinculoDeEntrenadorConOtroEquipoMantieneElRol() {
        UsuarioApp existente = new UsuarioApp();
        existente.setId(80);
        existente.setActivo(true);

        when(usuarioAppDao.obtenerPorId(80)).thenReturn(existente);
        when(usuarioAppVinculoDao.obtenerVinculos(80)).thenReturn(
                List.of(new VinculoUsuarioApp("ENTRENADOR", 200L, "Otro Equipo")));

        service.quitarVinculo(80, new VinculoSolicitado("ENTRENADOR", 100L));

        verify(usuarioAppVinculoDao).desvincularCuerpoTecnico(80, 100L);
        verify(rolAppDao, never()).eliminarRol(anyInt(), anyInt());
    }

    @Test
    void quitarVinculoDeEntrenadorSinOtroEquipoQuitaElRol() {
        UsuarioApp existente = new UsuarioApp();
        existente.setId(80);
        existente.setActivo(true);

        when(usuarioAppDao.obtenerPorId(80)).thenReturn(existente);
        when(usuarioAppVinculoDao.obtenerVinculos(80)).thenReturn(List.of());
        when(rolAppDao.obtenerPorCodigo("ENTRENADOR")).thenReturn(rol(6, "ENTRENADOR"));

        service.quitarVinculo(80, new VinculoSolicitado("ENTRENADOR", 100L));

        verify(usuarioAppVinculoDao).desvincularCuerpoTecnico(80, 100L);
        verify(rolAppDao).eliminarRol(80, 6);
    }

    @Test
    void quitarVinculoCoordinadorQuitaSiempreElRolSinTocarVinculosAPersona() {
        UsuarioApp existente = new UsuarioApp();
        existente.setId(90);
        existente.setActivo(true);

        when(usuarioAppDao.obtenerPorId(90)).thenReturn(existente);
        when(rolAppDao.obtenerPorCodigo("COORDINADOR")).thenReturn(rol(9, "COORDINADOR"));

        service.quitarVinculo(90, new VinculoSolicitado("COORDINADOR", null));

        verify(rolAppDao).eliminarRol(90, 9);
        verify(usuarioAppVinculoDao, never()).desvincularJugador(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).desvincularFamiliar(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).desvincularCuerpoTecnico(anyInt(), any());
    }

    @Test
    void quitarVinculoRetransmisionQuitaElRolSinTocarVinculosAPersona() {
        UsuarioApp existente = new UsuarioApp();
        existente.setId(91);
        existente.setActivo(true);

        when(usuarioAppDao.obtenerPorId(91)).thenReturn(existente);
        when(rolAppDao.obtenerPorCodigo("RETRANSMISION")).thenReturn(rol(10, "RETRANSMISION"));

        service.quitarVinculo(91, new VinculoSolicitado("RETRANSMISION", null));

        verify(rolAppDao).eliminarRol(91, 10);
        verify(usuarioAppVinculoDao, never()).desvincularJugador(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).desvincularFamiliar(anyInt(), any());
        verify(usuarioAppVinculoDao, never()).desvincularCuerpoTecnico(anyInt(), any());
    }

    @Test
    void quitarVinculoDeUsuarioInexistenteLanzaExcepcion() {
        when(usuarioAppDao.obtenerPorId(95)).thenReturn(null);

        assertThatThrownBy(() -> service.quitarVinculo(95, new VinculoSolicitado("JUGADOR", 1L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppVinculoDao, never()).desvincularJugador(anyInt(), any());
    }

    @Test
    void reenviarInvitacionDeUnaCuentaYaActivaLanzaExcepcion() {
        UsuarioApp activo = new UsuarioApp();
        activo.setId(3);
        activo.setActivo(true);

        when(usuarioAppDao.obtenerPorId(3)).thenReturn(activo);

        assertThatThrownBy(() -> service.reenviarInvitacion(3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está activa");
    }

    @Test
    void desactivarUsuarioAdminDeUnUsuarioInexistenteLanzaExcepcion() {
        when(usuarioAppDao.obtenerPorId(99)).thenReturn(null);

        assertThatThrownBy(() -> service.desactivarUsuarioAdmin(99))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppDao, never()).desactivarUsuario(anyInt());
    }

    @Test
    void eliminarInvitacionPendienteBorraVinculoRolesYCuenta() {
        UsuarioApp pendiente = new UsuarioApp();
        pendiente.setId(20);
        pendiente.setActivo(false);
        pendiente.setEmail("mal-escrito@mutxamelcf.es");

        when(usuarioAppDao.obtenerPorId(20)).thenReturn(pendiente);

        service.eliminarInvitacion(20);

        verify(usuarioAppVinculoDao).desvincularTodo(20);
        verify(rolAppDao).eliminarTodosLosRoles(20);
        verify(usuarioAppDao).eliminar(20);
    }

    @Test
    void eliminarInvitacionDeUnaCuentaYaActivaLanzaExcepcionYNoBorraNada() {
        UsuarioApp activo = new UsuarioApp();
        activo.setId(21);
        activo.setActivo(true);

        when(usuarioAppDao.obtenerPorId(21)).thenReturn(activo);

        assertThatThrownBy(() -> service.eliminarInvitacion(21))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya activa");

        verify(usuarioAppDao, never()).eliminar(anyInt());
        verify(usuarioAppVinculoDao, never()).desvincularTodo(anyInt());
    }

    @Test
    void eliminarInvitacionDeUnUsuarioInexistenteLanzaExcepcion() {
        when(usuarioAppDao.obtenerPorId(22)).thenReturn(null);

        assertThatThrownBy(() -> service.eliminarInvitacion(22))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioAppDao, never()).eliminar(anyInt());
    }
}
