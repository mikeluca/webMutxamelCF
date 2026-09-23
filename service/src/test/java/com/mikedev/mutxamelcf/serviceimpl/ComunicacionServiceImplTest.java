package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ComunicacionDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@ExtendWith(MockitoExtension.class)
class ComunicacionServiceImplTest {

    @Mock
    private ComunicacionDao comunicacionDao;

    @Mock
    private UsuarioAppService usuarioAppService;

    @Mock
    private NotificacionAppService notificacionAppService;

    @Mock
    private FcmPushService fcmPushService;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    private ComunicacionServiceImpl service;

    private static final Long USUARIO_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new ComunicacionServiceImpl(
                comunicacionDao, usuarioAppService, notificacionAppService, fcmPushService,
                usuarioAppVinculoDao);
    }

    private Comunicacion comunicacionValida() {
        Comunicacion comunicacion = new Comunicacion();
        comunicacion.setTitulo("Aviso");
        comunicacion.setContenido("Contenido del aviso");
        return comunicacion;
    }

    @Test
    void crearSinNingunDestinatarioLanzaExcepcion() {
        assertThatThrownBy(() -> service.crear(
                        comunicacionValida(), List.of(), List.of(), List.of(), USUARIO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debe especificarse");
    }

    @Test
    void crearConEquiposYCategoriasALaVezLanzaExcepcion() {
        assertThatThrownBy(() -> service.crear(
                        comunicacionValida(),
                        List.of(10L),
                        List.of("Infantil"),
                        List.of(),
                        USUARIO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no a varios tipos a la vez");
    }

    @Test
    void crearConEquiposYDestinatarioALaVezLanzaExcepcion() {
        when(comunicacionDao.obtenerDestinatariosDirectosPermitidos(USUARIO_ID))
                .thenReturn(List.of(99L));

        assertThatThrownBy(() -> service.crear(
                        comunicacionValida(),
                        List.of(10L),
                        List.of(),
                        List.of(99L),
                        USUARIO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no a varios tipos a la vez");
    }

    @Test
    void crearConMasDeUnDestinatarioPrivadoLanzaExcepcion() {
        when(comunicacionDao.obtenerDestinatariosDirectosPermitidos(USUARIO_ID))
                .thenReturn(List.of(99L, 100L));

        assertThatThrownBy(() -> service.crear(
                        comunicacionValida(),
                        List.of(),
                        List.of(),
                        List.of(99L, 100L),
                        USUARIO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("solo pueden dirigirse a una persona");
    }

    @Test
    void crearPrivadoSinTituloFuncionaCorrectamenteYSeGuardaConTituloNulo() {
        when(usuarioAppService.tieneRol(1, "ADMIN_APP")).thenReturn(false);
        when(usuarioAppService.tieneRol(1, "COORDINADOR")).thenReturn(true);
        when(comunicacionDao.obtenerDestinatariosDirectosPermitidos(USUARIO_ID))
                .thenReturn(List.of(99L));
        when(comunicacionDao.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(700L);

        Comunicacion comunicacion = new Comunicacion();
        comunicacion.setTitulo("Este título se debe ignorar");
        comunicacion.setContenido("Hola, ¿qué tal?");

        Comunicacion resultado = service.crear(
                comunicacion, List.of(), List.of(), List.of(99L), USUARIO_ID);

        assertThat(resultado.getId()).isEqualTo(700L);
        assertThat(resultado.getTitulo()).isNull();
        assertThat(resultado.getTipo()).isEqualTo("PRIVADA");
        org.mockito.Mockito.verify(comunicacionDao).guardarUsuario(700L, 99L);
    }

    @Test
    void crearGrupalSinTituloLanzaExcepcion() {
        Comunicacion comunicacion = new Comunicacion();
        comunicacion.setContenido("Contenido sin título");

        assertThatThrownBy(() -> service.crear(
                        comunicacion, List.of(10L), List.of(), List.of(), USUARIO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título es obligatorio");
    }

    @Test
    void crearSoloConEquiposFuncionaCorrectamente() {
        when(usuarioAppService.tieneRol(1, "ADMIN_APP")).thenReturn(false);
        when(usuarioAppService.tieneRol(1, "COORDINADOR")).thenReturn(true);
        when(comunicacionDao.existeEquipo(10L)).thenReturn(true);
        when(comunicacionDao.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(500L);
        when(comunicacionDao.obtenerUsuariosDelEquipo(10L)).thenReturn(List.of());

        Comunicacion resultado = service.crear(
                comunicacionValida(), List.of(10L), List.of(), List.of(), USUARIO_ID);

        assertThat(resultado.getId()).isEqualTo(500L);
        org.mockito.Mockito.verify(comunicacionDao).guardarEquipo(500L, 10L);
        org.mockito.Mockito.verify(comunicacionDao, org.mockito.Mockito.never())
                .guardarCategoria(anyLong(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(comunicacionDao, org.mockito.Mockito.never())
                .guardarUsuario(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void crearGrupalConDestinatarioComprubaLaPreferenciaDeMensajesNoLaDeComunicaciones() {
        when(usuarioAppService.tieneRol(1, "ADMIN_APP")).thenReturn(false);
        when(usuarioAppService.tieneRol(1, "COORDINADOR")).thenReturn(true);
        when(comunicacionDao.existeEquipo(10L)).thenReturn(true);
        when(comunicacionDao.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(500L);
        when(comunicacionDao.obtenerUsuariosDelEquipo(10L)).thenReturn(List.of(99L));
        when(notificacionAppService.puedeRecibir(99L, "MENSAJE")).thenReturn(true);

        service.crear(comunicacionValida(), List.of(10L), List.of(), List.of(), USUARIO_ID);

        // El interruptor "Mensajes" de la app (ajustes_page.dart) escribe
        // en la preferencia MENSAJE, no en COMUNICACION (esa la fuerza el
        // cliente siempre a true y no hay forma de desactivarla): si se
        // comprobara COMUNICACION, este envío nunca se bloquearía aunque
        // el usuario tenga los mensajes desactivados.
        org.mockito.Mockito.verify(notificacionAppService).puedeRecibir(99L, "MENSAJE");
        org.mockito.Mockito.verify(notificacionAppService, org.mockito.Mockito.never())
                .puedeRecibir(99L, "COMUNICACION");
        org.mockito.Mockito.verify(fcmPushService).enviarNotificacionAUsuario(
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("COMUNICACION"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void crearGrupalConMensajesDesactivadosNoNotificaAlDestinatario() {
        when(usuarioAppService.tieneRol(1, "ADMIN_APP")).thenReturn(false);
        when(usuarioAppService.tieneRol(1, "COORDINADOR")).thenReturn(true);
        when(comunicacionDao.existeEquipo(10L)).thenReturn(true);
        when(comunicacionDao.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(500L);
        when(comunicacionDao.obtenerUsuariosDelEquipo(10L)).thenReturn(List.of(99L));
        when(notificacionAppService.puedeRecibir(99L, "MENSAJE")).thenReturn(false);

        service.crear(comunicacionValida(), List.of(10L), List.of(), List.of(), USUARIO_ID);

        org.mockito.Mockito.verify(notificacionAppService, org.mockito.Mockito.never())
                .crear(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(fcmPushService, org.mockito.Mockito.never())
                .enviarNotificacionAUsuario(anyLong(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listarParaUsuarioNoIncluyeConversacionesPrivadas() {
        when(usuarioAppService.tieneRol(1, "ADMIN_APP")).thenReturn(true);

        Comunicacion grupal = new Comunicacion();
        grupal.setId(5L);
        grupal.setTitulo("Aviso");
        grupal.setContenido("Contenido");
        grupal.setTipo("GRUPAL");

        when(comunicacionDao.obtenerTodas()).thenReturn(List.of(grupal));

        List<com.mikedev.mutxamelcf.model.ComunicacionResponse> resultado =
                service.listarParaUsuario(USUARIO_ID);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTipo()).isEqualTo("GRUPAL");
        org.mockito.Mockito.verify(comunicacionDao, org.mockito.Mockito.never())
                .obtenerPrivadasDeUsuario(anyLong());
    }

    @Test
    void listarConversacionesParaUsuarioSoloDevuelvePrivadas() {
        Comunicacion mensaje = new Comunicacion();
        mensaje.setId(8L);
        mensaje.setContenido("Hola");
        mensaje.setUsuarioAutorId(99L);
        mensaje.setContraparteId(99L);
        mensaje.setTipo("PRIVADA");

        when(comunicacionDao.obtenerPrivadasDeUsuario(USUARIO_ID)).thenReturn(List.of(mensaje));
        when(notificacionAppService.obtenerNoLeidas(USUARIO_ID)).thenReturn(List.of());

        List<com.mikedev.mutxamelcf.model.ComunicacionResponse> resultado =
                service.listarConversacionesParaUsuario(USUARIO_ID);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTipo()).isEqualTo("PRIVADA");
        assertThat(resultado.get(0).getContraparteId()).isEqualTo(99L);
        org.mockito.Mockito.verify(comunicacionDao, org.mockito.Mockito.never())
                .obtenerTodas();
    }

    @Test
    void obtenerConversacionConUsuarioNoPermitidoLanzaExcepcion() {
        when(comunicacionDao.obtenerDestinatariosDirectosPermitidos(USUARIO_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.obtenerConversacion(USUARIO_ID, 99L))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void obtenerConversacionDevuelveMensajesConEsMiaCorrecto() {
        when(comunicacionDao.obtenerDestinatariosDirectosPermitidos(USUARIO_ID))
                .thenReturn(List.of(99L));

        Comunicacion mio = new Comunicacion();
        mio.setId(1L);
        mio.setContenido("Hola");
        mio.setUsuarioAutorId(USUARIO_ID);

        Comunicacion suyo = new Comunicacion();
        suyo.setId(2L);
        suyo.setContenido("¿Qué tal?");
        suyo.setUsuarioAutorId(99L);

        when(comunicacionDao.obtenerConversacion(USUARIO_ID, 99L))
                .thenReturn(List.of(mio, suyo));
        when(notificacionAppService.obtenerNoLeidas(USUARIO_ID)).thenReturn(List.of());

        List<com.mikedev.mutxamelcf.model.MensajeConversacionResponse> hilo =
                service.obtenerConversacion(USUARIO_ID, 99L);

        assertThat(hilo).hasSize(2);
        assertThat(hilo.get(0).isEsMia()).isTrue();
        assertThat(hilo.get(1).isEsMia()).isFalse();
    }

    @Test
    void marcarConversacionLeidaSoloMarcaLosMensajesDelOtroUsuario() {
        Comunicacion mio = new Comunicacion();
        mio.setId(1L);
        mio.setUsuarioAutorId(USUARIO_ID);

        Comunicacion suyo = new Comunicacion();
        suyo.setId(2L);
        suyo.setUsuarioAutorId(99L);

        when(comunicacionDao.obtenerConversacion(USUARIO_ID, 99L))
                .thenReturn(List.of(mio, suyo));

        service.marcarConversacionLeida(USUARIO_ID, 99L);

        org.mockito.Mockito.verify(notificacionAppService)
                .marcarLeidasPorReferencias(USUARIO_ID, List.of(2L));
    }
}
