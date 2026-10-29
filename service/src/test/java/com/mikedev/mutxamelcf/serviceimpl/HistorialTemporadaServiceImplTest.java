package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.CuerpoTecnicoTemporadaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoTemporadaFila;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.PartidosEquipo;
import com.mikedev.mutxamelcf.model.Temporada;

class HistorialTemporadaServiceImplTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final long PASADA = 1L;
	private static final long ACTIVA = 2L;
	private static final long CADETE_A = 5L;

	private TemporadaDao temporadaDao;
	private JugadorTemporadaDao jugadorTemporadaDao;
	private CuerpoTecnicoTemporadaDao cuerpoTecnicoTemporadaDao;
	private ClasificacionDao clasificacionDao;
	private EquipoDao equipoDao;
	private ResumenTemporadaDao resumenTemporadaDao;
	private HistorialTemporadaServiceImpl servicio;

	@BeforeEach
	void setUp() {
		temporadaDao = mock(TemporadaDao.class);
		jugadorTemporadaDao = mock(JugadorTemporadaDao.class);
		cuerpoTecnicoTemporadaDao = mock(CuerpoTecnicoTemporadaDao.class);
		clasificacionDao = mock(ClasificacionDao.class);
		equipoDao = mock(EquipoDao.class);
		resumenTemporadaDao = mock(ResumenTemporadaDao.class);

		// Hoy: 21/10/2026, dentro de la temporada activa y después de la pasada
		Clock reloj = Clock.fixed(LocalDate.of(2026, 10, 21).atTime(10, 0).atZone(MADRID).toInstant(), MADRID);
		servicio = new HistorialTemporadaServiceImpl(temporadaDao, jugadorTemporadaDao, cuerpoTecnicoTemporadaDao,
				clasificacionDao, equipoDao, resumenTemporadaDao, reloj);

		Temporada pasada = new Temporada(PASADA, "2025/2026", Date.valueOf("2025-07-01"), Date.valueOf("2026-06-30"), 0);
		Temporada activa = new Temporada(ACTIVA, "2026/2027", Date.valueOf("2026-07-01"), Date.valueOf("2027-06-30"), 1);
		when(temporadaDao.obtenerPorId(PASADA)).thenReturn(pasada);
		when(temporadaDao.obtenerPorId(ACTIVA)).thenReturn(activa);
		when(temporadaDao.obtenerTodos()).thenReturn(List.of(pasada, activa));

		Equipo cadete = new Equipo();
		cadete.setId(CADETE_A);
		cadete.setNombre("Cadete A");
		when(equipoDao.obtenerTodos()).thenReturn(List.of(cadete));
	}

	private static JugadorTemporada fila(long jugadorId, String nombre, Long equipoId, String equipoNombre, int dorsal) {
		JugadorTemporada f = new JugadorTemporada();
		f.setJugadorId(jugadorId);
		f.setJugadorNombre(nombre);
		f.setJugadorApellidos("Sanz");
		f.setEquipoId(equipoId);
		f.setEquipoNombre(equipoNombre);
		f.setDorsal(dorsal);
		f.setEstado("CONFIRMADO");
		return f;
	}

	@Test
	void lasTemporadasSalenLaMasNuevaPrimero() {
		assertThat(servicio.temporadas()).extracting(Temporada::getId).containsExactly(ACTIVA, PASADA);
	}

	@Test
	void unaTemporadaInexistenteSeRechaza() {
		assertThatThrownBy(() -> servicio.obtener(99L, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.obtener(null, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void lasPlantillasAgrupanLasFichasDeLaTemporadaPorEquipo() {
		when(jugadorTemporadaDao.listarPorTemporada(PASADA)).thenReturn(List.of(
				fila(1, "Pau", CADETE_A, "Cadete A", 4), fila(2, "Leo", CADETE_A, "Cadete A", 9),
				fila(3, "Noa", 6L, "Alevín A", 1)));

		HistorialTemporadaDTO historial = servicio.obtener(PASADA, null);

		assertThat(historial.plantillas()).extracting(HistorialTemporadaDTO.PlantillaEquipo::equipo)
				.containsExactly("Alevín A", "Cadete A");
		assertThat(historial.plantillas().get(1).jugadores()).extracting(HistorialTemporadaDTO.JugadorPlantilla::nombre)
				.containsExactly("Pau Sanz", "Leo Sanz");
		assertThat(historial.equiposConDatos()).extracting(HistorialTemporadaDTO.EquipoOpcion::nombre)
				.containsExactly("Alevín A", "Cadete A");
		assertThat(historial.estadisticas()).isNull();
		verify(resumenTemporadaDao, never()).partidosDelEquipo(any(), any(), any());
	}

	@Test
	void elCuerpoTecnicoSeAgrupaPorEquipo() {
		when(jugadorTemporadaDao.listarPorTemporada(PASADA)).thenReturn(List.of());
		when(cuerpoTecnicoTemporadaDao.listarPorTemporada(PASADA)).thenReturn(List.of(
				new CuerpoTecnicoTemporadaFila(CADETE_A, "Cadete A", 1L, "Luis", "Mora", "Entrenador"),
				new CuerpoTecnicoTemporadaFila(CADETE_A, "Cadete A", 2L, "Eva", "Gil", "Delegado")));

		HistorialTemporadaDTO historial = servicio.obtener(PASADA, null);

		assertThat(historial.cuerpoTecnico()).singleElement().satisfies(s -> {
			assertThat(s.equipo()).isEqualTo("Cadete A");
			assertThat(s.personas()).extracting(HistorialTemporadaDTO.PersonaStaff::nombre)
					.containsExactly("Luis Mora", "Eva Gil");
		});
	}

	@Test
	void laClasificacionFinalEsLaDeLaTemporadaPedida() {
		Clasificacion nuestra = new Clasificacion();
		nuestra.setEquipoId(CADETE_A);
		nuestra.setNombreEquipo("Mutxamel CF");
		nuestra.setEsNuestro(true);
		nuestra.setCompeticion("Liga Cadete");
		nuestra.setJornada(30);
		nuestra.setPosicion(1);
		nuestra.setPuntos(70);
		when(jugadorTemporadaDao.listarPorTemporada(PASADA)).thenReturn(List.of());
		when(clasificacionDao.listarPorTemporada(PASADA)).thenReturn(List.of(nuestra));

		HistorialTemporadaDTO historial = servicio.obtener(PASADA, null);

		assertThat(historial.clasificaciones()).singleElement().satisfies(t -> {
			assertThat(t.equipo()).isEqualTo("Cadete A");
			assertThat(t.competicion()).isEqualTo("Liga Cadete");
			assertThat(t.jornada()).isEqualTo(30);
			assertThat(t.filas()).singleElement().satisfies(f -> assertThat(f.puntos()).isEqualTo(70));
		});
		verify(clasificacionDao, never()).listarPorTemporada(ACTIVA);
	}

	@Test
	void lasEstadisticasDeUnaTemporadaPasadaUsanSusFechasYCuentanTodosSusPartidos() {
		LocalDate desde = LocalDate.of(2025, 7, 1);
		LocalDate hasta = LocalDate.of(2026, 6, 30);
		when(jugadorTemporadaDao.listarPorTemporada(PASADA)).thenReturn(List.of(fila(1, "Pau", CADETE_A, "Cadete A", 4)));
		when(resumenTemporadaDao.estadisticasJugadoresDelEquipo(CADETE_A, desde, hasta, hasta.plusDays(1)))
				.thenReturn(List.of(new EstadisticasJugadorEquipo(1L, "Pau Sanz", 4, 5, 2, 1, 0, 20),
						new EstadisticasJugadorEquipo(2L, "Leo Sanz", 9, 12, 0, 0, 0, 25)));
		when(resumenTemporadaDao.asistenciaEntrenamientosDelEquipo(CADETE_A, desde, hasta))
				.thenReturn(List.of(new AsistenciaJugadorEquipo(1L, "Pau Sanz", 4, 50, 45)));
		when(resumenTemporadaDao.partidosDelEquipo(CADETE_A, desde, hasta)).thenReturn(new PartidosEquipo(30, 22, 4, 4, 80, 20));
		when(resumenTemporadaDao.contarEntrenamientosDelEquipo(CADETE_A, desde, hasta, false)).thenReturn(50);
		when(resumenTemporadaDao.contarEntrenamientosDelEquipo(CADETE_A, desde, hasta, true)).thenReturn(48);

		HistorialTemporadaDTO.EstadisticasEquipo est = servicio.obtener(PASADA, CADETE_A).estadisticas();

		assertThat(est.equipo()).isEqualTo("Cadete A");
		assertThat(est.partidos().jugados()).isEqualTo(30);
		assertThat(est.sesiones()).isEqualTo(50);
		assertThat(est.sesionesRevisadas()).isEqualTo(48);
		// Ordenados por goles y con el entrenamiento de quien no tiene datos a cero
		assertThat(est.jugadores()).extracting(HistorialTemporadaDTO.JugadorEstadisticas::nombre)
				.containsExactly("Leo Sanz", "Pau Sanz");
		assertThat(est.jugadores().get(1).asistidos()).isEqualTo(45);
		assertThat(est.jugadores().get(0).entrenamientos()).isZero();
	}

	@Test
	void laTemporadaEnCursoSoloCuentaLosPartidosYaJugados() {
		LocalDate desde = LocalDate.of(2026, 7, 1);
		LocalDate hasta = LocalDate.of(2027, 6, 30);
		LocalDate hoy = LocalDate.of(2026, 10, 21);
		when(jugadorTemporadaDao.listarPorTemporada(ACTIVA)).thenReturn(List.of());
		when(resumenTemporadaDao.partidosDelEquipo(eq(CADETE_A), eq(desde), eq(hasta)))
				.thenReturn(new PartidosEquipo(0, 0, 0, 0, 0, 0));

		servicio.obtener(ACTIVA, CADETE_A);

		verify(resumenTemporadaDao).estadisticasJugadoresDelEquipo(CADETE_A, desde, hasta, hoy);
	}
}
