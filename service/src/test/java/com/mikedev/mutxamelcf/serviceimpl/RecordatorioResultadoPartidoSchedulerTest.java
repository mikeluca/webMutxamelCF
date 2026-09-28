package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;

@ExtendWith(MockitoExtension.class)
class RecordatorioResultadoPartidoSchedulerTest {

    @Mock
    private PartidoDao partidoDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private ComunicacionService comunicacionService;

    private RecordatorioResultadoPartidoScheduler scheduler;

    private static final Long EQUIPO_ID = 10L;
    private static final Long PARTIDO_ID = 20L;

    @BeforeEach
    void setUp() {
        scheduler = new RecordatorioResultadoPartidoScheduler(partidoDao, equipoGestionDao, comunicacionService);
    }

    private static Partido partidoPendiente(Long usuarioActualizoId) {
        Partido partido = new Partido();
        partido.setId(PARTIDO_ID);
        partido.setEquipoId(EQUIPO_ID);
        partido.setRival("Rival CF");
        partido.setDia(Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        partido.setUsuarioActualizoId(usuarioActualizoId);
        return partido;
    }

    @Test
    void noHaceNadaSiNoHayPartidosPendientes() {
        when(partidoDao.obtenerPendientesDeAvisoResultado(any())).thenReturn(List.of());

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao, never()).marcarAvisoResultadoEnviado(anyLong());
    }

    @Test
    void enviaUnaComunicacionACadaEntrenadorYMarcaElAvisoComoEnviado() {
        Partido partido = partidoPendiente(99L);

        when(partidoDao.obtenerPendientesDeAvisoResultado(any())).thenReturn(List.of(partido));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(100L, 200L));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        ArgumentCaptor<Comunicacion> captor = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(captor.capture(), eq(List.of(100L, 200L)), eq(99L));
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);

        assertThat(captor.getValue().getContenido()).contains("Rival CF");
    }

    @Test
    void marcaComoEnviadoAunqueNoHayaEntrenadoresParaEseEquipo() {
        Partido partido = partidoPendiente(99L);

        when(partidoDao.obtenerPendientesDeAvisoResultado(any())).thenReturn(List.of(partido));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of());

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);
    }

    @Test
    void usaUnCoordinadorComoAutorSiElPartidoNoTieneUsuarioQueLoActualizara() {
        Partido partido = partidoPendiente(null);

        when(partidoDao.obtenerPendientesDeAvisoResultado(any())).thenReturn(List.of(partido));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(100L));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(555L));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), eq(List.of(100L)), eq(555L));
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);
    }

    @Test
    void marcaComoEnviadoSinNotificarSiNoHayNingunAutorPosible() {
        Partido partido = partidoPendiente(null);

        when(partidoDao.obtenerPendientesDeAvisoResultado(any())).thenReturn(List.of(partido));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(100L));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of());

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);
    }
}
