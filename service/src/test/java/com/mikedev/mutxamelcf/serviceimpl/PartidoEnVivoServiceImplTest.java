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
import com.mikedev.mutxamelcf.service.FcmPushService;
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

    @Mock
    private FcmPushService fcmPushService;

    private PartidoEnVivoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PartidoEnVivoServiceImpl(
                partidoLiveDao, partidoDao, notificacionAppService, usuarioAppService, fcmPushService);
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
        verify(fcmPushService, never()).enviarATopic(anyString(), anyString(), anyString());
    }

    @Test
    void enviarInicioPartidoReiniciaElMarcadorYMandaComoResultado() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partidoRival());

        service.enviarInicioPartido(5L);

        verify(partidoLiveDao).reiniciar();
        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"), anyString(), eq("Mutxamel CF - CD Rival"), isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("⚽ ¡Comienza el partido!"), eq("Mutxamel CF - CD Rival"));
    }

    @Test
    void enviarGolFavorSumaElGolConElAutorYMuestraElMarcador() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(1, 0, List.of("Juan Perez")));

        service.enviarGolFavor(5L, "Juan Perez");

        verify(partidoLiveDao).sumarGolFavor("Juan Perez");
        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"),
                anyString(),
                eq("Juan Perez\n\nMutxamel CF 1 - 0 CD Rival"),
                isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("⚽ ¡GOOOL del Mutxamel CF!"), eq("Juan Perez\n\nMutxamel CF 1 - 0 CD Rival"));
    }

    @Test
    void enviarGolContraSumaElGolYMuestraElMarcador() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(0, 1, List.of()));

        service.enviarGolContra(5L);

        verify(partidoLiveDao).sumarGolContra();
        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"), anyString(), eq("Mutxamel CF 0 - 1 CD Rival"), isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("Gol en contra"), eq("Mutxamel CF 0 - 1 CD Rival"));
    }

    @Test
    void enviarDescansoEnviaElMarcadorActual() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(1, 0, List.of("Juan Perez")));

        service.enviarDescanso(5L);

        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"), anyString(), eq("Mutxamel CF 1 - 0 CD Rival"), isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("⏸️ Descanso"), eq("Mutxamel CF 1 - 0 CD Rival"));
    }

    @Test
    void enviarSegundaParteAnunciaElReinicio() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partidoRival());

        service.enviarSegundaParte(5L);

        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"), anyString(), eq("Mutxamel CF - CD Rival"), isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("▶️ ¡Comienza la segunda parte!"), eq("Mutxamel CF - CD Rival"));
    }

    @Test
    void enviarFinalPartidoIncluyeElMarcadorYLosGoleadores() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partidoRival());
        when(partidoLiveDao.obtenerEstado()).thenReturn(
                new PartidoLiveEstado(2, 1, List.of("Juan Perez", "Ana Gomez")));

        service.enviarFinalPartido(5L);

        String mensajeEsperado = "Mutxamel CF 2 - 1 CD Rival"
                + "\n\nGoleadores: Juan Perez, Ana Gomez"
                + "\n\nHa finalizado el partido.";

        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"),
                anyString(),
                eq(mensajeEsperado),
                isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("🏁 Final del partido"), eq(mensajeEsperado));
    }

    @Test
    void enviarAlineacionComponeElMensajeConLosDosTextos() {

        when(usuarioAppService.tieneRol(5, "RETRANSMISION")).thenReturn(true);

        service.enviarAlineacion(5L, "Portero, Defensas...", "Suplente 1, Suplente 2");

        String mensajeEsperado = "Once inicial: Portero, Defensas...\n\nSuplentes: Suplente 1, Suplente 2";

        verify(notificacionAppService).difundirATodos(
                eq("RESULTADO"),
                anyString(),
                eq(mensajeEsperado),
                isNull());
        verify(fcmPushService).enviarATopic(
                eq("resultados"), eq("📋 Alineación del Mutxamel CF"), eq(mensajeEsperado));

        verify(partidoLiveDao, never()).reiniciar();
        verify(partidoLiveDao, never()).obtenerEstado();
    }
}
