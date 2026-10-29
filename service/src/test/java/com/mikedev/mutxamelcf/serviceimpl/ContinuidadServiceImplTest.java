package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.ContinuidadJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.ResultadoPreguntaContinuidadDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

class ContinuidadServiceImplTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final long SIGUIENTE = 7L;

	private NuevaTemporadaService nuevaTemporadaService;
	private JugadorTemporadaDao jugadorTemporadaDao;
	private PerfilAppDao perfilAppDao;
	private UsuarioAppVinculoDao vinculoDao;
	private EquipoGestionDao equipoGestionDao;
	private NotificacionAppService notificacionAppService;
	private FcmPushService fcmPushService;
	private Temporada siguiente;
	private ContinuidadServiceImpl servicio;

	@BeforeEach
	void setUp() {
		nuevaTemporadaService = mock(NuevaTemporadaService.class);
		jugadorTemporadaDao = mock(JugadorTemporadaDao.class);
		CategoriaPorEdad categoriaPorEdad = mock(CategoriaPorEdad.class);
		perfilAppDao = mock(PerfilAppDao.class);
		vinculoDao = mock(UsuarioAppVinculoDao.class);
		equipoGestionDao = mock(EquipoGestionDao.class);
		notificacionAppService = mock(NotificacionAppService.class);
		fcmPushService = mock(FcmPushService.class);

		siguiente = new Temporada(SIGUIENTE, "2027/2028", Date.valueOf("2027-07-01"), Date.valueOf("2028-06-30"), 0);
		when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of(siguiente));
		when(categoriaPorEdad.obtenerTodas()).thenReturn(List.of(new CategoriaEdad("Alevín", 10, 11, 4),
				new CategoriaEdad("Cadete", 14, 15, 6)));
		when(notificacionAppService.puedeRecibir(anyLong(), anyString())).thenReturn(true);

		// Hoy: lunes 10/05/2027
		Clock reloj = Clock.fixed(LocalDate.of(2027, 5, 10).atTime(10, 0).atZone(MADRID).toInstant(), MADRID);
		servicio = new ContinuidadServiceImpl(nuevaTemporadaService, jugadorTemporadaDao, categoriaPorEdad, perfilAppDao,
				vinculoDao, equipoGestionDao, notificacionAppService, fcmPushService, reloj);
	}

	private static JugadorTemporada fila(long jugadorId, String nombre, String categoria, String estado,
			String continuidad, LocalDateTime pregunta) {
		JugadorTemporada f = new JugadorTemporada();
		f.setJugadorId(jugadorId);
		f.setTemporadaId(SIGUIENTE);
		f.setJugadorNombre(nombre);
		f.setJugadorApellidos("Pérez");
		f.setCategoria(categoria);
		f.setEstado(estado);
		f.setContinuidad(continuidad);
		f.setFechaPreguntaContinuidad(pregunta);
		return f;
	}

	private void cuentas(long jugadorId, Long... familiares) {
		when(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(jugadorId)).thenReturn(List.of(familiares));
		when(equipoGestionDao.obtenerUsuariosPorJugador(jugadorId)).thenReturn(List.of());
	}

	// ---- preguntar ----------------------------------------------------------------------------------

	@Test
	void pedirConfirmacionPreguntaSoloALosFormativosPendientesYMarcaLaFecha() {
		JugadorTemporada pablo = fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", null);
		JugadorTemporada adulto = fila(2, "Ana", "Primer Equipo", "CONFIRMADO", "SIGUE", null);
		JugadorTemporada prueba = fila(3, "Biel", "Alevín", "PRUEBA", "SIGUE", null);
		JugadorTemporada preguntado = fila(4, "Eva", "Cadete", "CONFIRMADO", "PENDIENTE", LocalDateTime.of(2027, 5, 1, 9, 0));
		when(nuevaTemporadaService.generarPropuesta(SIGUIENTE)).thenReturn(4);
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of(pablo, adulto, prueba, preguntado));
		cuentas(1, 50L, 51L);

		ResultadoPreguntaContinuidadDTO resultado = servicio.pedirConfirmacion(SIGUIENTE);

		assertThat(resultado.filasCreadas()).isEqualTo(4);
		assertThat(resultado.jugadoresPreguntados()).isEqualTo(1);
		assertThat(resultado.usuariosAvisados()).isEqualTo(2);
		assertThat(pablo.getFechaPreguntaContinuidad()).isEqualTo(LocalDateTime.of(2027, 5, 10, 10, 0));
		verify(jugadorTemporadaDao, times(1)).guardar(any());
		verify(jugadorTemporadaDao).guardar(pablo);
	}

	@Test
	void elPushLlevaLaPreguntaYLosDatosParaAbrirLaPantalla() {
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE))
				.thenReturn(List.of(fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", null)));
		cuentas(1, 50L);

		servicio.pedirConfirmacion(SIGUIENTE);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
		verify(notificacionAppService).crear(50L, "CONTINUIDAD", "Continuidad en 2027/2028",
				"¿Seguirá Pablo en la temporada 2027/2028?", SIGUIENTE);
		verify(fcmPushService).enviarNotificacionAUsuario(eq(50L), eq("CONTINUIDAD"), eq("Continuidad en 2027/2028"),
				eq("¿Seguirá Pablo en la temporada 2027/2028?"), eq(SIGUIENTE), datos.capture());
		assertThat(datos.getValue()).containsEntry("pantalla", "CONTINUIDAD").containsEntry("accion", "PREGUNTA")
				.containsEntry("temporadaId", "7").containsEntry("jugadorIds", "1");
	}

	@Test
	void unaCuentaConVariosJugadoresRecibeUnSoloAviso() {
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of(
				fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", null),
				fila(2, "Luis", "Cadete", "CONFIRMADO", "PENDIENTE", null)));
		cuentas(1, 50L);
		cuentas(2, 50L);

		ResultadoPreguntaContinuidadDTO resultado = servicio.pedirConfirmacion(SIGUIENTE);

		assertThat(resultado.usuariosAvisados()).isEqualTo(1);
		verify(fcmPushService, times(1)).enviarNotificacionAUsuario(eq(50L), eq("CONTINUIDAD"), anyString(),
				eq("¿Seguirán Pablo y Luis en la temporada 2027/2028?"), eq(SIGUIENTE),
				org.mockito.ArgumentMatchers.<Map<String, String>>argThat(m -> "1,2".equals(m.get("jugadorIds"))));
	}

	@Test
	void repetirLaPeticionNoVuelveAAvisar() {
		JugadorTemporada ya = fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", LocalDateTime.of(2027, 5, 1, 9, 0));
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of(ya));
		cuentas(1, 50L);

		ResultadoPreguntaContinuidadDTO resultado = servicio.pedirConfirmacion(SIGUIENTE);

		assertThat(resultado.jugadoresPreguntados()).isZero();
		verify(fcmPushService, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
		verify(jugadorTemporadaDao, never()).guardar(any());
	}

	@Test
	void sinCuentasVinculadasElJugadorQuedaPreguntadoDeTodosModos() {
		JugadorTemporada pablo = fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", null);
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of(pablo));
		cuentas(1);

		ResultadoPreguntaContinuidadDTO resultado = servicio.pedirConfirmacion(SIGUIENTE);

		assertThat(resultado.jugadoresPreguntados()).isEqualTo(1);
		assertThat(resultado.usuariosAvisados()).isZero();
		assertThat(pablo.getFechaPreguntaContinuidad()).isNotNull();
	}

	@Test
	void unaCuentaQueNoQuiereAvisosNoRecibeNada() {
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE))
				.thenReturn(List.of(fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", null)));
		cuentas(1, 50L);
		when(notificacionAppService.puedeRecibir(50L, "COMUNICACION")).thenReturn(false);

		servicio.pedirConfirmacion(SIGUIENTE);

		verify(notificacionAppService, never()).crear(anyLong(), anyString(), anyString(), anyString(), anyLong());
		verify(fcmPushService, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
	}

	@Test
	void unFalloDeEnvioNoRompeLaPregunta() {
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE))
				.thenReturn(List.of(fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", null),
						fila(2, "Luis", "Cadete", "CONFIRMADO", "PENDIENTE", null)));
		cuentas(1, 50L);
		cuentas(2, 51L);
		org.mockito.Mockito.doThrow(new RuntimeException("fcm caído")).when(fcmPushService)
				.enviarNotificacionAUsuario(eq(50L), anyString(), anyString(), anyString(), anyLong(), anyMap());

		ResultadoPreguntaContinuidadDTO resultado = servicio.pedirConfirmacion(SIGUIENTE);

		assertThat(resultado.jugadoresPreguntados()).isEqualTo(2);
		verify(fcmPushService).enviarNotificacionAUsuario(eq(51L), anyString(), anyString(), anyString(), anyLong(),
				anyMap());
	}

	@Test
	void elJobDelUnoDeMayoNoHaceNadaSiNoExisteLaTemporadaSiguiente() {
		when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of());

		assertThat(servicio.pedirConfirmacionSiHaySiguiente()).isNull();
		verify(nuevaTemporadaService, never()).generarPropuesta(any());
	}

	@Test
	void elJobDelUnoDeMayoPideLaConfirmacionDeLaSiguiente() {
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of());

		assertThat(servicio.pedirConfirmacionSiHaySiguiente().temporadaId()).isEqualTo(SIGUIENTE);
		verify(nuevaTemporadaService).generarPropuesta(SIGUIENTE);
	}

	// ---- recordatorio -------------------------------------------------------------------------------

	@Test
	void elRecordatorioVaSoloAQuienYaFuePreguntadoYSigueSinContestar() {
		LocalDateTime preguntado = LocalDateTime.of(2027, 5, 1, 9, 0);
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of(
				fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", preguntado),
				fila(2, "Luis", "Cadete", "CONFIRMADO", "SIGUE", preguntado),
				fila(3, "Eva", "Cadete", "CONFIRMADO", "PENDIENTE", null)));
		cuentas(1, 50L);
		cuentas(2, 51L);
		cuentas(3, 52L);

		int avisadas = servicio.recordarPendientes();

		assertThat(avisadas).isEqualTo(1);
		verify(fcmPushService).enviarNotificacionAUsuario(eq(50L), eq("CONTINUIDAD"),
				eq("Recordatorio: continuidad en 2027/2028"),
				eq("Aún no nos has contestado. ¿Seguirá Pablo en la temporada 2027/2028?"), eq(SIGUIENTE),
				org.mockito.ArgumentMatchers.<Map<String, String>>argThat(m -> "RECORDATORIO".equals(m.get("accion"))));
		verify(fcmPushService, never()).enviarNotificacionAUsuario(eq(51L), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
		verify(fcmPushService, never()).enviarNotificacionAUsuario(eq(52L), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
	}

	@Test
	void elRecordatorioSePararaAlEmpezarLaTemporadaNuevaOSiNoHaySiguiente() {
		when(jugadorTemporadaDao.listarPorTemporada(SIGUIENTE)).thenReturn(List.of(
				fila(1, "Pablo", "Alevín", "CONFIRMADO", "PENDIENTE", LocalDateTime.of(2027, 5, 1, 9, 0))));
		cuentas(1, 50L);
		Clock enJulio = Clock.fixed(LocalDate.of(2027, 7, 5).atTime(10, 0).atZone(MADRID).toInstant(), MADRID);
		ContinuidadServiceImpl tarde = new ContinuidadServiceImpl(nuevaTemporadaService, jugadorTemporadaDao,
				mock(CategoriaPorEdad.class), perfilAppDao, vinculoDao, equipoGestionDao, notificacionAppService,
				fcmPushService, enJulio);

		assertThat(tarde.recordarPendientes()).isZero();

		when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of());
		assertThat(servicio.recordarPendientes()).isZero();
		verify(fcmPushService, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
	}

	// ---- listar -------------------------------------------------------------------------------------

	@Test
	void listarDevuelveSoloLosJugadoresDelUsuarioQueTienenQueConfirmar() {
		Jugador pablo = new Jugador();
		pablo.setId(1L);
		Jugador luis = new Jugador();
		luis.setId(2L);
		Jugador sinFila = new Jugador();
		sinFila.setId(3L);
		when(perfilAppDao.obtenerJugadoresPorUsuario(50)).thenReturn(List.of(pablo, luis, sinFila));
		when(jugadorTemporadaDao.obtener(1L, SIGUIENTE))
				.thenReturn(fila(1, "Pablo", "Alevín", "CONFIRMADO", "DUDA", LocalDateTime.of(2027, 5, 1, 9, 0)));
		when(jugadorTemporadaDao.obtener(2L, SIGUIENTE)).thenReturn(fila(2, "Luis", "Cadete", "CONFIRMADO", "SIGUE", null));

		List<ContinuidadJugadorDTO> lista = servicio.listar(50L);

		assertThat(lista).singleElement().satisfies(c -> {
			assertThat(c.jugadorId()).isEqualTo(1L);
			assertThat(c.nombre()).isEqualTo("Pablo Pérez");
			assertThat(c.temporada()).isEqualTo("2027/2028");
			assertThat(c.respuesta()).isEqualTo("DUDA");
		});
	}

	@Test
	void sinTemporadaSiguienteLaListaEstaVacia() {
		when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of());

		assertThat(servicio.listar(50L)).isEmpty();
	}

	// ---- responder ----------------------------------------------------------------------------------

	private void jugadorPreguntado(String continuidad) {
		when(vinculoDao.tieneVinculoConJugador(50, 1L)).thenReturn(true);
		when(jugadorTemporadaDao.obtener(1L, SIGUIENTE))
				.thenReturn(fila(1, "Pablo", "Alevín", "CONFIRMADO", continuidad, LocalDateTime.of(2027, 5, 1, 9, 0)));
		when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(90L, 91L));
	}

	@Test
	void sinVinculoConElJugadorNoSePuedeResponder() {
		when(vinculoDao.tieneVinculoConJugador(50, 1L)).thenReturn(false);

		assertThatThrownBy(() -> servicio.responder(50L, 1L, "SIGUE")).isInstanceOf(SecurityException.class);
		verify(nuevaTemporadaService, never()).marcarContinuidad(any(), any(), any(), any());
	}

	@Test
	void responderGuardaLaRespuestaConElUsuarioQueLaDa() {
		jugadorPreguntado("PENDIENTE");

		servicio.responder(50L, 1L, "sigue");

		verify(nuevaTemporadaService).marcarContinuidad(SIGUIENTE, 1L, "SIGUE", 50L);
		verify(fcmPushService, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
	}

	@Test
	void unaRespuestaNoValidaOUnJugadorNoPreguntadoSeRechazan() {
		jugadorPreguntado("PENDIENTE");

		assertThatThrownBy(() -> servicio.responder(50L, 1L, "TAL_VEZ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.responder(50L, 1L, "PENDIENTE")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.responder(50L, 1L, null)).isInstanceOf(IllegalArgumentException.class);

		when(jugadorTemporadaDao.obtener(1L, SIGUIENTE)).thenReturn(fila(1, "Pablo", "Alevín", "CONFIRMADO", "SIGUE", null));
		assertThatThrownBy(() -> servicio.responder(50L, 1L, "SIGUE")).hasMessageContaining("no tiene que confirmar");
		when(jugadorTemporadaDao.obtener(1L, SIGUIENTE)).thenReturn(null);
		assertThatThrownBy(() -> servicio.responder(50L, 1L, "SIGUE")).hasMessageContaining("no tiene que confirmar");
		verify(nuevaTemporadaService, never()).marcarContinuidad(any(), any(), any(), any());
	}

	@Test
	void sinTemporadaSiguienteNoSePuedeResponder() {
		when(vinculoDao.tieneVinculoConJugador(50, 1L)).thenReturn(true);
		when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of());

		assertThatThrownBy(() -> servicio.responder(50L, 1L, "SIGUE")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void noSigueAvisaATodosLosCoordinadoresConLosDatosParaAbrirLaPantalla() {
		jugadorPreguntado("PENDIENTE");

		servicio.responder(50L, 1L, "NO_SIGUE");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
		verify(notificacionAppService).crear(eq(90L), eq("CONTINUIDAD_RESPUESTA"), eq("No seguirá: Pablo Pérez (Alevín)"),
				anyString(), eq(1L));
		verify(notificacionAppService).crear(eq(91L), eq("CONTINUIDAD_RESPUESTA"), anyString(), anyString(), eq(1L));
		verify(fcmPushService).enviarNotificacionAUsuario(eq(90L), eq("CONTINUIDAD_RESPUESTA"), anyString(), anyString(),
				eq(1L), datos.capture());
		assertThat(datos.getValue()).containsEntry("pantalla", "CONTINUIDAD_COORDINADOR").containsEntry("jugadorId", "1")
				.containsEntry("temporadaId", "7").containsEntry("respuesta", "NO_SIGUE");
	}

	@Test
	void dudaTambienAvisaPeroSigueNo() {
		jugadorPreguntado("PENDIENTE");
		servicio.responder(50L, 1L, "DUDA");
		verify(fcmPushService, times(2)).enviarNotificacionAUsuario(anyLong(), eq("CONTINUIDAD_RESPUESTA"),
				eq("Duda sobre la continuidad: Pablo Pérez (Alevín)"), anyString(), eq(1L), anyMap());
	}

	@Test
	void repetirLaMismaRespuestaNoVuelveAAvisarALosCoordinadores() {
		jugadorPreguntado("NO_SIGUE");

		servicio.responder(50L, 1L, "NO_SIGUE");

		verify(fcmPushService, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(),
				anyLong(), anyMap());
	}

	// ---- altas de prueba -----------------------------------------------------------------------------

	@Test
	void soloElCoordinadorDaDeAltaPruebasYSeHaceEnLaTemporadaSiguiente() {
		when(equipoGestionDao.esCoordinadorOAdmin(8L)).thenReturn(false);
		when(equipoGestionDao.esCoordinadorOAdmin(9L)).thenReturn(true);
		JugadorTemporada creada = fila(5, "Luis", "Alevín", "PRUEBA", "SIGUE", null);
		when(nuevaTemporadaService.anadirPrueba(SIGUIENTE, "Luis", "Gil", 2016)).thenReturn(creada);

		assertThatThrownBy(() -> servicio.altaPrueba(8L, "Luis", "Gil", 2016)).isInstanceOf(SecurityException.class);
		assertThat(servicio.altaPrueba(9L, "Luis", "Gil", 2016)).isSameAs(creada);
	}

	@Test
	void sinTemporadaSiguienteNoSeDanAltasDePrueba() {
		when(equipoGestionDao.esCoordinadorOAdmin(9L)).thenReturn(true);
		when(nuevaTemporadaService.temporadasDestino()).thenReturn(List.of());

		assertThatThrownBy(() -> servicio.altaPrueba(9L, "Luis", "Gil", 2016)).isInstanceOf(IllegalStateException.class);
	}
}
