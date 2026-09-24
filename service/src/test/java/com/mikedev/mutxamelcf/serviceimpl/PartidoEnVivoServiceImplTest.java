package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveDao;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoLiveEstado;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@ExtendWith(MockitoExtension.class)
class PartidoEnVivoServiceImplTest {

    @Mock
    private PartidoLiveDao partidoLiveDao;

    @Mock
    private PartidoDao partidoDao;

    @Mock
    private NotificacionAppService notificacionAppService;

    @Mock
    private UsuarioAppService usuarioAppService;

    private PartidoEnVivoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PartidoEnVivoServiceImpl(
                partidoLiveDao, partidoDao, notificacionAppService, usuarioAppService);
    }

    private Partido partidoRival() {
        Partido partido = new Partido();
        partido.setRival("CD Rival");
        return partido;
    }

    @Test
    void enviarInicioPartidoSinRolRetransmisionLanzaExcepcionYNoHaceNada() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(false);

        assertThatThrownBy(() -> service.enviarInicioPartido(5L))
                .isInstanceOf(SecurityException.class);

        verify(partidoLiveDao, never()).reiniciar();
        verify(notificacionAppService, never())
                .difundirATodos(anyString(), anyString(), anyString(), any());
    }

    @Test
    void enviarInicioPartidoReiniciaElMarcadorYMandaComoResultado() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(partidoRival());

        service.enviarInicioPartido(5L);

        verify(partidoLiveDao).reiniciar();
        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"), anyString(), eq("Mutxamel CF - CD Rival"), isNull());
    }

    @Test
    void enviarGolFavorSumaElGolConElAutorYMuestraElMarcador() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(1, 0, List.of("Juan Perez")));

        service.enviarGolFavor(5L, "Juan Perez");

        verify(partidoLiveDao).sumarGolFavor("Juan Perez");
        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"),
                anyString(),
                eq("Juan Perez\n\nMutxamel CF 1 - 0 CD Rival"),
                isNull());
    }

    @Test
    void enviarGolContraSumaElGolYMuestraElMarcador() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(0, 1, List.of()));

        service.enviarGolContra(5L);

        verify(partidoLiveDao).sumarGolContra();
        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"), anyString(), eq("Mutxamel CF 0 - 1 CD Rival"), isNull());
    }

    @Test
    void enviarFinalPartidoIncluyeElMarcadorYLosGoleadores() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(2, 1, List.of("Juan Perez", "Ana Gomez")));

        service.enviarFinalPartido(5L);

        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"),
                anyString(),
                eq("Mutxamel CF 2 - 1 CD Rival"
                        + "\n\nGoleadores: Juan Perez, Ana Gomez"
                        + "\n\nHa finalizado el partido."),
                isNull());
    }

    @Test
    void enviarAlineacionComponeElMensajeConLosDosTextos() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);

        service.enviarAlineacion(5L, "Portero, Defensas...", "Suplente 1, Suplente 2");

        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"),
                anyString(),
                eq("Once inicial: Portero, Defensas...\n\nSuplentes: Suplente 1, Suplente 2"),
                isNull());

        verify(partidoLiveDao, never()).reiniciar();
        verify(partidoLiveDao, never()).obtenerEstado();
    }
}
