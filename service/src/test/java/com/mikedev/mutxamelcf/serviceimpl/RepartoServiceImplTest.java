package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AsignacionRepartoRequest;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoRepartoDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.PruebasSinConfirmarException;
import com.mikedev.mutxamelcf.model.RepartoCategoriaDTO;
import com.mikedev.mutxamelcf.model.ResultadoRepartoDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo;
import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo.AsignacionDefinitiva;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;

class RepartoServiceImplTest {

	private static final long TEMPORADA = 5L;
	private static final long PRE = 10L;
	private static final long ALEVIN_A = 11L;
	private static final long ALEVIN_B = 12L;

	private TemporadaDao temporadaDao;
	private EquipoDao equipoDao;
	private JugadorDao jugadorDao;
	private JugadorTemporadaDao jugadorTemporadaDao;
	private CategoriaPorEdad categoriaPorEdad;
	private EquipoGestionDao equipoGestionDao;
	private List<AsignacionDefinitiva> eventos;
	private RepartoServiceImpl servicio;

	private List<Equipo> equipos;
	private List<JugadorTemporada> filas;

	@BeforeEach
	void setUp() {
		temporadaDao = mock(TemporadaDao.class);
		equipoDao = mock(EquipoDao.class);
		jugadorDao = mock(JugadorDao.class);
		jugadorTemporadaDao = mock(JugadorTemporadaDao.class);
		categoriaPorEdad = mock(CategoriaPorEdad.class);
		equipoGestionDao = mock(EquipoGestionDao.class);
		eventos = new ArrayList<>();

		Temporada activa = new Temporada();
		activa.setId(TEMPORADA);
		when(temporadaDao.obtenerTemporadaActiva()).thenReturn(activa);
		when(categoriaPorEdad.obtenerTodas()).thenReturn(List.of(new CategoriaEdad("Alevín", 10, 11, 4),
				new CategoriaEdad("Benjamín", 8, 9, 3)));

		equipos = new ArrayList<>(List.of(equipo(PRE, "Alevín (Pretemporada)", "P", "PRETEMPORADA", true),
				equipo(ALEVIN_A, "Alevín A", "A", "NORMAL", false), equipo(ALEVIN_B, "Alevín B", "B", "NORMAL", true)));
		when(equipoDao.obtenerTodos()).thenAnswer(i -> equipos);

		filas = new ArrayList<>();
		when(jugadorTemporadaDao.listarPorTemporada(TEMPORADA)).thenAnswer(i -> filas);

		AlAsignarEquipoDefinitivo hook = eventos::add;
		servicio = new RepartoServiceImpl(temporadaDao, equipoDao, jugadorDao, jugadorTemporadaDao, categoriaPorEdad,
				equipoGestionDao, List.of(hook));
	}

	private static Equipo equipo(long id, String nombre, String grupo, String tipo, boolean activo) {
		Equipo e = new Equipo();
		e.setId(id);
		e.setNombre(nombre);
		e.setCategoria("Alevín");
		e.setGrupo(grupo);
		e.setOrden("4");
		e.setDeporte("F");
		e.setModalidad("F8");
		e.setTipoTemporada(tipo);
		e.setActivoTemporada(activo);
		return e;
	}

	private JugadorTemporada fila(long jugadorId, String nombre, String estado, Long equipoId, boolean definitivo,
			Integer dorsal) {
		JugadorTemporada f = new JugadorTemporada();
		f.setJugadorId(jugadorId);
		f.setTemporadaId(TEMPORADA);
		f.setJugadorNombre(nombre);
		f.setJugadorApellidos("Pérez");
		f.setEstado(estado);
		f.setCategoria("Alevín");
		f.setEquipoId(equipoId);
		f.setEquipoDefinitivo(definitivo);
		f.setDorsal(dorsal);
		filas.add(f);
		return f;
	}

	private static AsignacionRepartoRequest peticion(long equipoId, boolean confirmar,
			AsignacionRepartoRequest.Jugador... jugadores) {
		return new AsignacionRepartoRequest(equipoId, List.of(jugadores), confirmar);
	}

	// ---- acceso ------------------------------------------------------------------------------------

	@Test
	void soloElCoordinadorPuedeRepartir() {
		when(equipoGestionDao.esCoordinadorOAdmin(7L)).thenReturn(false);
		when(equipoGestionDao.esCoordinadorOAdmin(8L)).thenReturn(true);

		assertThatThrownBy(() -> servicio.comprobarCoordinador(7L)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> servicio.comprobarCoordinador(null)).isInstanceOf(SecurityException.class);
		servicio.comprobarCoordinador(8L);
	}

	// ---- consulta ----------------------------------------------------------------------------------

	@Test
	void lasCategoriasSonLasFormativasConEquiposEnLaTemporada() {
		assertThat(servicio.categorias()).containsExactly("Alevín");
	}

	@Test
	void elRepartoDeUnaCategoriaSeparaPorAsignarYEquiposDefinitivos() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, 3);
		fila(2, "Biel", "PRUEBA", null, false, null);
		fila(3, "Carla", "CONFIRMADO", ALEVIN_B, true, 9);
		JugadorTemporada baja = fila(4, "Dani", "BAJA", PRE, false, null);
		baja.setBaja(true);

		RepartoCategoriaDTO reparto = servicio.obtenerCategoria("alevín");

		assertThat(reparto.categoria()).isEqualTo("Alevín");
		assertThat(reparto.pretemporada().equipoId()).isEqualTo(PRE);
		assertThat(reparto.porAsignar()).extracting(j -> j.jugadorId()).containsExactly(1L, 2L);
		assertThat(reparto.equipos()).extracting(EquipoRepartoDTO::equipoId).containsExactly(ALEVIN_A, ALEVIN_B);
		assertThat(reparto.equipos().get(1).jugadores()).extracting(j -> j.jugadorId()).containsExactly(3L);
	}

	@Test
	void unaCategoriaNoFormativaSeRechaza() {
		assertThatThrownBy(() -> servicio.obtenerCategoria("Senior")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no es una categoría formativa");
	}

	// ---- asignar -----------------------------------------------------------------------------------

	@Test
	void asignarActualizaJugadorYFichaDeLaTemporadaYActivaElEquipoConSuPrimerJugador() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		fila(2, "Eva", "CONFIRMADO", ALEVIN_B, true, 4); // la pretemporada sigue con Ana hasta que se vaya

		ResultadoRepartoDTO resultado = servicio.asignar(peticion(ALEVIN_A, false,
				new AsignacionRepartoRequest.Jugador(1L, 7)));

		assertThat(resultado.asignados()).isEqualTo(1);
		assertThat(resultado.equipoActivado()).isTrue();
		assertThat(resultado.avisos()).isEmpty();
		verify(jugadorDao).asignarPlantilla(1L, ALEVIN_A, "Alevín A", "Alevín", "F", 7);

		ArgumentCaptor<JugadorTemporada> guardada = ArgumentCaptor.forClass(JugadorTemporada.class);
		verify(jugadorTemporadaDao).guardar(guardada.capture());
		assertThat(guardada.getValue().getEquipoId()).isEqualTo(ALEVIN_A);
		assertThat(guardada.getValue().getDorsal()).isEqualTo(7);
		assertThat(guardada.getValue().isEquipoDefinitivo()).isTrue();
		verify(equipoDao).marcarActivoEnTemporada(ALEVIN_A, true);
	}

	@Test
	void unEquipoYaActivoNoSeVuelveAActivar() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);

		ResultadoRepartoDTO resultado = servicio.asignar(peticion(ALEVIN_B, false,
				new AsignacionRepartoRequest.Jugador(1L, null)));

		assertThat(resultado.equipoActivado()).isFalse();
		verify(equipoDao, never()).marcarActivoEnTemporada(eq(ALEVIN_B), anyBoolean());
	}

	@Test
	void cuandoLaPretemporadaSeQuedaVaciaSeDesactiva() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		when(jugadorTemporadaDao.contarEnEquipoEnLaTemporadaActiva(PRE)).thenReturn(0);

		ResultadoRepartoDTO resultado = servicio.asignar(peticion(ALEVIN_B, false,
				new AsignacionRepartoRequest.Jugador(1L, 1)));

		assertThat(resultado.pretemporadaVaciada()).isTrue();
		verify(equipoDao).marcarActivoEnTemporada(PRE, false);
	}

	@Test
	void mientrasLaPretemporadaTengaJugadoresSigueActiva() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		when(jugadorTemporadaDao.contarEnEquipoEnLaTemporadaActiva(PRE)).thenReturn(2);

		ResultadoRepartoDTO resultado = servicio.asignar(peticion(ALEVIN_B, false,
				new AsignacionRepartoRequest.Jugador(1L, 1)));

		assertThat(resultado.pretemporadaVaciada()).isFalse();
		verify(equipoDao, never()).marcarActivoEnTemporada(eq(PRE), anyBoolean());
	}

	@Test
	void unDorsalRepetidoAvisaPeroNoImpideAsignar() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		fila(2, "Eva", "CONFIRMADO", ALEVIN_B, true, 7);

		ResultadoRepartoDTO resultado = servicio.asignar(peticion(ALEVIN_B, false,
				new AsignacionRepartoRequest.Jugador(1L, 7)));

		assertThat(resultado.asignados()).isEqualTo(1);
		assertThat(resultado.avisos()).singleElement().asString().contains("dorsal 7").contains("Ana").contains("Eva");
	}

	@Test
	void unJugadorDePruebaNecesitaConfirmacionYNoSeAsignaNada() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		fila(2, "Biel", "PRUEBA", null, false, null);

		AsignacionRepartoRequest sinConfirmar = peticion(ALEVIN_B, false,
				new AsignacionRepartoRequest.Jugador(1L, 1), new AsignacionRepartoRequest.Jugador(2L, 2));

		assertThatThrownBy(() -> servicio.asignar(sinConfirmar)).isInstanceOf(PruebasSinConfirmarException.class)
				.satisfies(e -> assertThat(((PruebasSinConfirmarException) e).getPruebas()).containsExactly("Biel Pérez"));
		verify(jugadorDao, never()).asignarPlantilla(anyLong(), anyLong(), any(), any(), any(), any());
		verify(jugadorTemporadaDao, never()).guardar(any());
	}

	@Test
	void conConfirmacionLaPruebaPasaAConfirmado() {
		JugadorTemporada prueba = fila(2, "Biel", "PRUEBA", null, false, null);

		ResultadoRepartoDTO resultado = servicio.asignar(peticion(ALEVIN_B, true,
				new AsignacionRepartoRequest.Jugador(2L, 2)));

		assertThat(resultado.pruebasConfirmadas()).containsExactly("Biel Pérez");
		assertThat(prueba.getEstado()).isEqualTo("CONFIRMADO");
		assertThat(eventos).singleElement().satisfies(ev -> assertThat(ev.eraPrueba()).isTrue());
	}

	@Test
	void elHookSeAvisaPorCadaJugadorAsignado() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		fila(2, "Eva", "CONFIRMADO", PRE, false, null);

		servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(1L, 1),
				new AsignacionRepartoRequest.Jugador(2L, 2)));

		assertThat(eventos).extracting(AsignacionDefinitiva::jugadorId).containsExactly(1L, 2L);
		assertThat(eventos).allSatisfy(ev -> {
			assertThat(ev.equipoId()).isEqualTo(ALEVIN_B);
			assertThat(ev.temporadaId()).isEqualTo(TEMPORADA);
			assertThat(ev.eraPrueba()).isFalse();
		});
	}

	@Test
	void validacionesDeLaAsignacion() {
		fila(1, "Ana", "CONFIRMADO", PRE, false, null);
		JugadorTemporada baja = fila(3, "Dani", "BAJA", PRE, false, null);
		baja.setBaja(true);
		JugadorTemporada otraCategoria = fila(4, "Fran", "CONFIRMADO", null, false, null);
		otraCategoria.setCategoria("Benjamín");

		assertThatThrownBy(() -> servicio.asignar(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.asignar(new AsignacionRepartoRequest(ALEVIN_B, List.of(), false)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos un jugador");
		assertThatThrownBy(() -> servicio.asignar(peticion(99, false, new AsignacionRepartoRequest.Jugador(1L, 1))))
				.hasMessageContaining("no existe");
		assertThatThrownBy(() -> servicio.asignar(peticion(PRE, false, new AsignacionRepartoRequest.Jugador(1L, 1))))
				.hasMessageContaining("no es un equipo definitivo");
		assertThatThrownBy(() -> servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(1L, 100))))
				.hasMessageContaining("entre 0 y 99");
		assertThatThrownBy(() -> servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(1L, -1))))
				.hasMessageContaining("entre 0 y 99");
		assertThatThrownBy(() -> servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(3L, 1))))
				.hasMessageContaining("está de baja");
		assertThatThrownBy(() -> servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(4L, 1))))
				.hasMessageContaining("no es de la categoría");
		assertThatThrownBy(() -> servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(77L, 1))))
				.hasMessageContaining("no tiene ficha");
		assertThatThrownBy(() -> servicio.asignar(peticion(ALEVIN_B, false, new AsignacionRepartoRequest.Jugador(1L, 1),
				new AsignacionRepartoRequest.Jugador(1L, 2)))).hasMessageContaining("repetido");

		verify(jugadorDao, never()).asignarPlantilla(anyLong(), anyLong(), any(), any(), any(), any());
	}

	// ---- crear equipo ------------------------------------------------------------------------------

	@Test
	void unEquipoNuevoLlevaLaSiguienteLetraLaModalidadDeLaPretemporadaYNaceInactivo() {
		Equipo creado = equipo(40, "Alevín C", "C", "NORMAL", false);
		when(equipoDao.guardar(any())).thenReturn(true);
		when(equipoDao.obtenerEquipoPorNombre("Alevín C")).thenReturn(creado);

		EquipoRepartoDTO dto = servicio.crearEquipo("Alevín");

		ArgumentCaptor<Equipo> guardado = ArgumentCaptor.forClass(Equipo.class);
		verify(equipoDao).guardar(guardado.capture());
		assertThat(guardado.getValue().getNombre()).isEqualTo("Alevín C");
		assertThat(guardado.getValue().getGrupo()).isEqualTo("C");
		assertThat(guardado.getValue().getModalidad()).isEqualTo("F8");
		assertThat(guardado.getValue().getOrden()).isEqualTo("4");
		assertThat(guardado.getValue().getActivoTemporada()).isFalse();
		assertThat(dto.equipoId()).isEqualTo(40L);
	}

	@Test
	void laLetraPNuncaSeUsaParaUnEquipoNuevo() {
		equipos.add(equipo(20, "Alevín O", "O", "NORMAL", true));
		when(equipoDao.guardar(any())).thenReturn(true);
		when(equipoDao.obtenerEquipoPorNombre(any())).thenReturn(equipo(41, "Alevín Q", "Q", "NORMAL", false));

		servicio.crearEquipo("Alevín");

		ArgumentCaptor<Equipo> guardado = ArgumentCaptor.forClass(Equipo.class);
		verify(equipoDao).guardar(guardado.capture());
		assertThat(guardado.getValue().getGrupo()).isEqualTo("Q");
	}

	@Test
	void sinEquiposEnLaCategoriaEmpiezaPorLaA() {
		equipos.clear();
		when(equipoDao.guardar(any())).thenReturn(true);
		when(equipoDao.obtenerEquipoPorNombre(any())).thenReturn(equipo(42, "Alevín A", "A", "NORMAL", false));

		servicio.crearEquipo("Alevín");

		ArgumentCaptor<Equipo> guardado = ArgumentCaptor.forClass(Equipo.class);
		verify(equipoDao).guardar(guardado.capture());
		assertThat(guardado.getValue().getNombre()).isEqualTo("Alevín A");
		assertThat(guardado.getValue().getDeporte()).isEqualTo("F");
	}

	@Test
	void noSeCreanEquiposDeCategoriasNoFormativas() {
		assertThatThrownBy(() -> servicio.crearEquipo("Senior")).isInstanceOf(IllegalArgumentException.class);
		verify(equipoDao, never()).guardar(any());
	}
}
