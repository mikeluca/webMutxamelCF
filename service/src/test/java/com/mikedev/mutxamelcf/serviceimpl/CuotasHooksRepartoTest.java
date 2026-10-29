package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo.AsignacionDefinitiva;
import com.mikedev.mutxamelcf.service.AlQuitarEquipoDefinitivo.QuitaDefinitiva;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService.ResultadoRetiradaCuotas;

/** Los hooks del reparto que generan y retiran las cuotas del jugador. */
class CuotasHooksRepartoTest {

	private static final long TEMPORADA = 5L;
	private static final long EQUIPO = 11L;

	private CuotasTemporadaService cuotas;
	private JugadorTemporadaDao fichas;
	private EquipoDao equipoDao;
	private JugadorDao jugadorDao;
	private CuotasAlAsignarEquipoDefinitivo alAsignar;
	private CuotasAlQuitarEquipoDefinitivo alQuitar;

	@BeforeEach
	void setUp() {
		cuotas = mock(CuotasTemporadaService.class);
		fichas = mock(JugadorTemporadaDao.class);
		equipoDao = mock(EquipoDao.class);
		jugadorDao = mock(JugadorDao.class);
		alAsignar = new CuotasAlAsignarEquipoDefinitivo(cuotas, fichas, equipoDao);
		alQuitar = new CuotasAlQuitarEquipoDefinitivo(cuotas, jugadorDao);

		Equipo definitivo = new Equipo();
		definitivo.setId(EQUIPO);
		definitivo.setTipoTemporada("NORMAL");
		when(equipoDao.obtenerEquipoPorId(EQUIPO)).thenReturn(definitivo);
	}

	private JugadorTemporada ficha(String estado, boolean definitivo, boolean baja) {
		JugadorTemporada f = new JugadorTemporada();
		f.setJugadorId(1L);
		f.setTemporadaId(TEMPORADA);
		f.setEstado(estado);
		f.setEquipoDefinitivo(definitivo);
		f.setBaja(baja);
		when(fichas.obtener(1L, TEMPORADA)).thenReturn(f);
		return f;
	}

	private AsignacionDefinitiva asignacion(boolean eraPrueba) {
		return new AsignacionDefinitiva(1L, EQUIPO, TEMPORADA, eraPrueba, false);
	}

	@Test
	void unJugadorConfirmadoConEquipoDefinitivoRecibeSusCuotas() {
		ficha("CONFIRMADO", true, false);

		alAsignar.alAsignar(asignacion(false));

		verify(cuotas).crearCuotasDelJugador(1L);
	}

	@Test
	void unaPruebaQueAcabaDeConfirmarseTambienLasRecibe() {
		// Al asignarle el equipo definitivo pasa de PRUEBA a CONFIRMADO: ya no es una prueba
		ficha("CONFIRMADO", true, false);

		alAsignar.alAsignar(asignacion(true));

		verify(cuotas).crearCuotasDelJugador(1L);
	}

	@Test
	void nuncaSeCreanCuotasAUnJugadorQueSigueEnPrueba() {
		ficha("PRUEBA", true, false);

		alAsignar.alAsignar(asignacion(true));

		verify(cuotas, never()).crearCuotasDelJugador(1L);
	}

	@Test
	void nuncaParaUnEquipoDePretemporadaNiSinEquipoDefinitivoNiDeBaja() {
		ficha("CONFIRMADO", true, false);
		Equipo pretemporada = new Equipo();
		pretemporada.setId(EQUIPO);
		pretemporada.setTipoTemporada("PRETEMPORADA");
		when(equipoDao.obtenerEquipoPorId(EQUIPO)).thenReturn(pretemporada);
		alAsignar.alAsignar(asignacion(false));

		ficha("CONFIRMADO", false, false);
		alAsignar.alAsignar(asignacion(false));

		ficha("BAJA", true, true);
		alAsignar.alAsignar(asignacion(false));

		verify(cuotas, never()).crearCuotasDelJugador(1L);
	}

	@Test
	void sinFichaDeLaTemporadaNoSeCreaNada() {
		when(fichas.obtener(1L, TEMPORADA)).thenReturn(null);

		alAsignar.alAsignar(asignacion(false));

		verify(cuotas, never()).crearCuotasDelJugador(1L);
	}

	@Test
	void unEquipoQueNoSeEncuentraNoImpideCrearLasCuotas() {
		ficha("CONFIRMADO", true, false);
		when(equipoDao.obtenerEquipoPorId(EQUIPO)).thenThrow(new EmptyResultDataAccessException(1));

		alAsignar.alAsignar(asignacion(false));

		verify(cuotas).crearCuotasDelJugador(1L);
	}

	@Test
	void moverAUnJugadorEntreDosEquiposDefinitivosNoCreaCuotas() {
		ficha("CONFIRMADO", true, false);

		alAsignar.alAsignar(new AsignacionDefinitiva(1L, EQUIPO, TEMPORADA, false, true));

		verify(cuotas, never()).crearCuotasDelJugador(1L);
	}

	@Test
	void laPrimeraAsignacionDesdePretemporadaCreaLasCuotas() {
		ficha("CONFIRMADO", true, false);

		alAsignar.alAsignar(new AsignacionDefinitiva(1L, EQUIPO, TEMPORADA, false, false));

		verify(cuotas).crearCuotasDelJugador(1L);
	}

	@Test
	void unaPruebaConfirmadaAlAsignarlaTambienLasCrea() {
		ficha("CONFIRMADO", true, false);

		alAsignar.alAsignar(new AsignacionDefinitiva(1L, EQUIPO, TEMPORADA, true, false));

		verify(cuotas).crearCuotasDelJugador(1L);
	}

	// ---- quitar ---------------------------------------------------------------------------------

	@Test
	void alDevolverAPretemporadaSeRetiranLasCuotasSinAvisosSiNoHabiaPagos() {
		when(cuotas.retirarCuotasDelJugador(1L)).thenReturn(new ResultadoRetiradaCuotas(4, 0, List.of()));

		assertThat(alQuitar.alQuitar(new QuitaDefinitiva(1L, EQUIPO, TEMPORADA))).isEmpty();
	}

	@Test
	void conPagosAvisaDeLasCuotasQueSeConservan() {
		when(cuotas.retirarCuotasDelJugador(1L)).thenReturn(new ResultadoRetiradaCuotas(3, 2, List.of(7L, 8L)));
		Jugador ana = new Jugador();
		ana.setNombre("Ana");
		ana.setApellidos("Pérez");
		when(jugadorDao.obtenerPorId(1L)).thenReturn(ana);

		List<String> avisos = alQuitar.alQuitar(new QuitaDefinitiva(1L, EQUIPO, TEMPORADA));

		assertThat(avisos).containsExactly(
				"Ana Pérez tiene 2 cuotas con pagos que no se han borrado: revísalas en Pagos.");
	}

	@Test
	void conUnaSolaCuotaConPagosElAvisoVaEnSingular() {
		when(cuotas.retirarCuotasDelJugador(1L)).thenReturn(new ResultadoRetiradaCuotas(0, 1, List.of(7L)));
		when(jugadorDao.obtenerPorId(1L)).thenReturn(null);

		assertThat(alQuitar.alQuitar(new QuitaDefinitiva(1L, EQUIPO, TEMPORADA))).singleElement().asString()
				.contains("1 cuota con pagos que no se ha borrado");
	}
}
