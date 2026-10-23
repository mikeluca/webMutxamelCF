package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.DemasiadoPronto;
import com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.Enviado;
import com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.SinEntrenadores;

class RecordatorioEntrenadorServiceImplTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final Long COORDINADOR = 7L;
	private static final Long PARTIDO = 30L;
	private static final Long EQUIPO = 10L;

	/** Reloj que se adelanta a mano. */
	private static final class RelojManual extends java.time.Clock {
		private Instant ahora;

		RelojManual(LocalDateTime inicio) {
			this.ahora = inicio.atZone(MADRID).toInstant();
		}

		void avanzar(Duration duracion) {
			ahora = ahora.plus(duracion);
		}

		@Override
		public ZoneId getZone() {
			return MADRID;
		}

		@Override
		public java.time.Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return ahora;
		}
	}

	private PartidoDao partidoDao;
	private ConvocatoriaDao convocatoriaDao;
	private EquipoGestionDao equipoGestionDao;
	private ComunicacionService comunicacionService;
	private RelojManual reloj;
	private RecordatorioEntrenadorServiceImpl service;

	@BeforeEach
	void setUp() {
		partidoDao = mock(PartidoDao.class);
		convocatoriaDao = mock(ConvocatoriaDao.class);
		equipoGestionDao = mock(EquipoGestionDao.class);
		comunicacionService = mock(ComunicacionService.class);
		// Miércoles 21/10/2026, 12:00.
		reloj = new RelojManual(LocalDate.of(2026, 10, 21).atTime(12, 0));

		when(equipoGestionDao.esCoordinadorOAdmin(COORDINADOR)).thenReturn(true);
		when(equipoGestionDao.obtenerNombreEquipo(EQUIPO)).thenReturn("Cadete A");
		when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(50L, 51L));

		service = new RecordatorioEntrenadorServiceImpl(
				partidoDao, convocatoriaDao, equipoGestionDao, comunicacionService, reloj);
	}

	private Partido partido(LocalDate dia, String hora) {
		Partido partido = new Partido();
		partido.setId(PARTIDO);
		partido.setEquipoId(EQUIPO);
		partido.setRival("Pego C.F.");
		partido.setDia(Date.valueOf(dia));
		partido.setHora(hora);
		when(partidoDao.obtenerPorId(PARTIDO)).thenReturn(partido);
		return partido;
	}

	// ---- rol ---------------------------------------------------------------------------

	@Test
	void soloCoordinadorOAdminPuedenRecordar() {
		partido(LocalDate.of(2026, 10, 24), "11:00");
		when(equipoGestionDao.esCoordinadorOAdmin(COORDINADOR)).thenReturn(false);

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> service.enviar(null, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(SecurityException.class);

		verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
	}

	// ---- convocatoria ------------------------------------------------------------------

	@Test
	void recordarLaConvocatoriaAvisaALosEntrenadoresConElPartidoYElTipo() {
		partido(LocalDate.of(2026, 10, 24), "11:00");

		Enviado enviado = service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA");

		assertThat(enviado.destinatarios()).isEqualTo(2);
		assertThat(enviado.fecha()).isEqualTo(LocalDateTime.of(2026, 10, 21, 12, 0));

		ArgumentCaptor<Comunicacion> aviso = ArgumentCaptor.forClass(Comunicacion.class);
		ArgumentCaptor<OpcionesPushComunicacion> opciones = ArgumentCaptor.forClass(OpcionesPushComunicacion.class);
		verify(comunicacionService).crearPrivada(aviso.capture(), eq(List.of(50L, 51L)), eq(COORDINADOR),
				opciones.capture());

		assertThat(aviso.getValue().getContenido())
				.isEqualTo("Recuerda publicar la convocatoria del partido contra Pego C.F. (24/10/2026 11:00)");
		assertThat(opciones.getValue().datos())
				.containsEntry("tipo", "CONVOCATORIA_PENDIENTE")
				.containsEntry("equipoId", "10")
				.containsEntry("equipo", "Cadete A")
				.containsEntry("rival", "Pego C.F.")
				.containsEntry("fecha", "2026-10-24")
				.containsEntry("hora", "11:00")
				.containsEntry("local", "true")
				.containsEntry("partidoId", "30");
	}

	@Test
	void laConvocatoriaSoloSiNoLaTieneNoEstaCanceladoYNoHaPasado() {
		Partido partido = partido(LocalDate.of(2026, 10, 24), "11:00");

		when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(new Convocatoria());
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ya tiene convocatoria");

		when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(null);
		partido.setCancelado(true);
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cancelado");

		partido.setCancelado(false);
		partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 20)));
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ya ha pasado");

		// El de hoy todavía vale.
		partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 21)));
		assertThat(service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA").destinatarios()).isEqualTo(2);
	}

	// ---- resultado ---------------------------------------------------------------------

	@Test
	void recordarElResultadoDeUnPartidoPendiente() {
		partido(LocalDate.of(2026, 10, 18), "11:00");

		service.enviar(COORDINADOR, PARTIDO, "resultado");

		ArgumentCaptor<Comunicacion> aviso = ArgumentCaptor.forClass(Comunicacion.class);
		ArgumentCaptor<OpcionesPushComunicacion> opciones = ArgumentCaptor.forClass(OpcionesPushComunicacion.class);
		verify(comunicacionService).crearPrivada(aviso.capture(), eq(List.of(50L, 51L)), eq(COORDINADOR),
				opciones.capture());

		assertThat(aviso.getValue().getContenido())
				.isEqualTo("Falta el resultado del partido contra Pego C.F. (18/10/2026)");
		assertThat(opciones.getValue().datos())
				.containsEntry("tipo", "RESULTADO_PENDIENTE")
				.containsEntry("partidoId", "30");
	}

	@Test
	void elResultadoSoloSiEstaPendienteConElCriterioCompartido() {
		Partido partido = partido(LocalDate.of(2026, 10, 18), "11:00");

		// Con resultado (por goles o por texto).
		partido.setGolesFavor(2);
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "RESULTADO"))
				.isInstanceOf(IllegalArgumentException.class);

		partido.setGolesFavor(null);
		partido.setResultado("2-1");
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "RESULTADO"))
				.isInstanceOf(IllegalArgumentException.class);

		// " - " cuenta como vacío.
		partido.setResultado(" - ");
		partido.setCancelado(true);
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "RESULTADO"))
				.isInstanceOf(IllegalArgumentException.class);

		// Todavía no ha terminado (empieza a las 11:00 de hoy, son las 12:00).
		partido.setCancelado(false);
		partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 21)));
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "RESULTADO"))
				.isInstanceOf(IllegalArgumentException.class);

		// Ya terminado y sin resultado.
		partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 20)));
		assertThat(service.enviar(COORDINADOR, PARTIDO, "RESULTADO").destinatarios()).isEqualTo(2);
	}

	@Test
	void unTipoOUnPartidoNoValidosSonError() {
		partido(LocalDate.of(2026, 10, 24), "11:00");

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "OTRO"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.enviar(COORDINADOR, 999L, "CONVOCATORIA"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ---- entrenadores y límite ---------------------------------------------------------

	@Test
	void sinEntrenadoresConCuentaEsUnConflictoYNoGastaElLimite() {
		partido(LocalDate.of(2026, 10, 24), "11:00");
		when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of());

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(SinEntrenadores.class)
				.isInstanceOfSatisfying(SinEntrenadores.class, e -> assertThat(e.codigo()).isEqualTo("SIN_ENTRENADORES"))
                .hasMessage("El equipo no tiene entrenadores con cuenta en la app");

		verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());

		// Al crearse una cuenta de entrenador, se puede recordar enseguida.
		when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(50L));
		assertThat(service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA").destinatarios()).isEqualTo(1);
	}

	@Test
	void unRecordatorioPorPartidoYTipoCadaDosHoras() {
		partido(LocalDate.of(2026, 10, 18), "11:00");

		service.enviar(COORDINADOR, PARTIDO, "RESULTADO");
		LocalDateTime primero = LocalDateTime.of(2026, 10, 21, 12, 0);

		reloj.avanzar(Duration.ofMinutes(119));

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "RESULTADO"))
				.isInstanceOfSatisfying(DemasiadoPronto.class, e -> assertThat(e.ultimo()).isEqualTo(primero));

		verify(comunicacionService, times(1)).crearPrivada(any(), any(), anyLong(), any());

		// A las 2 horas ya vale.
		reloj.avanzar(Duration.ofMinutes(1));
		service.enviar(COORDINADOR, PARTIDO, "RESULTADO");

		verify(comunicacionService, times(2)).crearPrivada(any(), any(), anyLong(), any());
	}

	@Test
	void dosLlamadasSeguidasSoloEnvianUnaVez() {
		partido(LocalDate.of(2026, 10, 24), "11:00");

		service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA");

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(DemasiadoPronto.class);

		verify(comunicacionService, times(1)).crearPrivada(any(), any(), anyLong(), any());
	}

	@Test
	void siElEnvioFallaSeLiberaLaReserva() {
		partido(LocalDate.of(2026, 10, 24), "11:00");
		when(comunicacionService.crearPrivada(any(), any(), anyLong(), any()))
				.thenThrow(new IllegalStateException("fallo"))
				.thenReturn(null);

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(IllegalStateException.class);

		// No ha gastado el límite: se puede reintentar enseguida.
		assertThat(service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA").destinatarios()).isEqualTo(2);
	}

	@Test
	void elCoordinadorNoEsDestinatarioDeSuPropioRecordatorio() {
		partido(LocalDate.of(2026, 10, 24), "11:00");
		when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(COORDINADOR, 50L));

		Enviado enviado = service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA");

		assertThat(enviado.destinatarios()).isEqualTo(1);
		verify(comunicacionService).crearPrivada(any(), eq(List.of(50L)), eq(COORDINADOR), any());
	}

	@Test
	void siSoloEntrenaElCoordinadorNoHayEntrenadores() {
		partido(LocalDate.of(2026, 10, 24), "11:00");
		when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(COORDINADOR));

		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOfSatisfying(SinEntrenadores.class, e -> assertThat(e.codigo()).isEqualTo("UNICO_ENTRENADOR"))
                .hasMessage("Eres el único entrenador con cuenta de este equipo");

		verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
	}

	@Test
	void elLimiteEsPorPartidoYPorTipo() {
		Partido partido = partido(LocalDate.of(2026, 10, 24), "11:00");

		service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA");

		// Otro partido, mismo tipo.
		Partido otro = new Partido();
		otro.setId(31L);
		otro.setEquipoId(EQUIPO);
		otro.setRival("Elx");
		otro.setDia(partido.getDia());
		otro.setHora("12:00");
		when(partidoDao.obtenerPorId(31L)).thenReturn(otro);

		assertThat(service.enviar(COORDINADOR, 31L, "CONVOCATORIA").destinatarios()).isEqualTo(2);

		// Mismo partido, otro tipo: ya ha pasado la fecha del partido para el resultado.
		partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 18)));
		assertThat(service.enviar(COORDINADOR, PARTIDO, "RESULTADO").destinatarios()).isEqualTo(2);

		// El primero sigue limitado.
		partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 24)));
		assertThatThrownBy(() -> service.enviar(COORDINADOR, PARTIDO, "CONVOCATORIA"))
				.isInstanceOf(DemasiadoPronto.class);
	}
}
