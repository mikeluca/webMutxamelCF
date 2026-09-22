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

    private ComunicacionServiceImpl service;

    private static final Long USUARIO_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new ComunicacionServiceImpl(
                comunicacionDao, usuarioAppService, notificacionAppService, fcmPushService);
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
}
