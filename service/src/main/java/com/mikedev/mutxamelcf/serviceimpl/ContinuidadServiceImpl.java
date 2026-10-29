package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import com.mikedev.mutxamelcf.service.ContinuidadService;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

@Service
public class ContinuidadServiceImpl implements ContinuidadService {

	private static final Logger logger = LoggerFactory.getLogger(ContinuidadServiceImpl.class);

	/** Tipo (y valor de {@code tipo} en el data del push) de la pregunta y del recordatorio a las familias. */
	static final String TIPO_FAMILIA = "CONTINUIDAD";

	/** Tipo del aviso a los coordinadores cuando una familia responde NO_SIGUE o DUDA. */
	static final String TIPO_COORDINADOR = "CONTINUIDAD_RESPUESTA";

	static final String PANTALLA_FAMILIA = "CONTINUIDAD";
	static final String PANTALLA_COORDINADOR = "CONTINUIDAD_COORDINADOR";

	private static final Set<String> RESPUESTAS = Set.of("SIGUE", "NO_SIGUE", "DUDA");

	private final NuevaTemporadaService nuevaTemporadaService;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final CategoriaPorEdad categoriaPorEdad;
	private final PerfilAppDao perfilAppDao;
	private final UsuarioAppVinculoDao vinculoDao;
	private final EquipoGestionDao equipoGestionDao;
	private final NotificacionAppService notificacionAppService;
	private final FcmPushService fcmPushService;
	private final Clock clock;

	public ContinuidadServiceImpl(NuevaTemporadaService nuevaTemporadaService, JugadorTemporadaDao jugadorTemporadaDao,
			CategoriaPorEdad categoriaPorEdad, PerfilAppDao perfilAppDao, UsuarioAppVinculoDao vinculoDao,
			EquipoGestionDao equipoGestionDao, NotificacionAppService notificacionAppService,
			FcmPushService fcmPushService, Clock clock) {
		this.nuevaTemporadaService = nuevaTemporadaService;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.categoriaPorEdad = categoriaPorEdad;
		this.perfilAppDao = perfilAppDao;
		this.vinculoDao = vinculoDao;
		this.equipoGestionDao = equipoGestionDao;
		this.notificacionAppService = notificacionAppService;
		this.fcmPushService = fcmPushService;
		this.clock = clock;
	}

	// ============================================================
	// Temporada siguiente
	// ============================================================

	@Override
	public Temporada temporadaSiguiente() {
		List<Temporada> destinos = nuevaTemporadaService.temporadasDestino();

		return destinos.isEmpty() ? null : destinos.get(0);
	}

	private Temporada siguienteObligatoria() {
		Temporada siguiente = temporadaSiguiente();

		if (siguiente == null) {
			throw new IllegalStateException("No hay una temporada siguiente: todavía no se ha creado.");
		}

		return siguiente;
	}

	private static LocalDate aLocalDate(java.util.Date fecha) {
		return fecha instanceof java.sql.Date sql ? sql.toLocalDate()
				: fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}

	private Set<String> categoriasFormativas() {
		return categoriaPorEdad.obtenerTodas().stream().map(CategoriaEdad::getCategoria)
				.map(c -> c.trim().toLowerCase()).collect(Collectors.toSet());
	}

	private static boolean esFormativoPendiente(JugadorTemporada f, Set<String> formativas) {
		return "CONFIRMADO".equals(f.getEstado()) && "PENDIENTE".equals(f.getContinuidad()) && !f.isJugadorDeBaja()
				&& f.getCategoria() != null && formativas.contains(f.getCategoria().trim().toLowerCase());
	}

	/** Las cuentas que pueden contestar por el jugador: las de sus familiares y la del propio jugador. */
	private Set<Long> cuentasDe(Long jugadorId) {
		Set<Long> cuentas = new LinkedHashSet<>(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(jugadorId));
		cuentas.addAll(equipoGestionDao.obtenerUsuariosPorJugador(jugadorId));

		return cuentas;
	}

	// ============================================================
	// Preguntar
	// ============================================================

	@Override
	@Transactional
	public ResultadoPreguntaContinuidadDTO pedirConfirmacion(Long destinoId) {
		int creadas = nuevaTemporadaService.generarPropuesta(destinoId);
		Temporada destino = nuevaTemporadaService.temporadasDestino().stream().filter(t -> destinoId.equals(t.getId()))
				.findFirst().orElseThrow(() -> new IllegalArgumentException("La temporada de destino no existe."));
		Set<String> formativas = categoriasFormativas();
		LocalDateTime ahora = LocalDateTime.now(clock);

		// Quién tiene que contestar por qué jugadores
		Map<Long, List<JugadorTemporada>> porCuenta = new LinkedHashMap<>();
		int preguntados = 0;

		for (JugadorTemporada fila : jugadorTemporadaDao.listarPorTemporada(destinoId)) {
			if (fila.getFechaPreguntaContinuidad() != null || !esFormativoPendiente(fila, formativas)) {
				continue;
			}

			// Se marca aunque no haya cuentas: si una familia se vincula después, ya verá la pregunta en la app
			fila.setFechaPreguntaContinuidad(ahora);
			jugadorTemporadaDao.guardar(fila);
			preguntados++;

			for (Long cuenta : cuentasDe(fila.getJugadorId())) {
				porCuenta.computeIfAbsent(cuenta, k -> new java.util.ArrayList<>()).add(fila);
			}
		}

		int usuarios = porCuenta.size();
		despuesDelCommit(() -> porCuenta.forEach((cuenta, filas) -> avisarFamilia(cuenta, filas, destino, false)));

		logger.info("Confirmación de continuidad pedida: destino={}, filasCreadas={}, preguntados={}, cuentas={}",
				destino.getNombre(), creadas, preguntados, usuarios);

		return new ResultadoPreguntaContinuidadDTO(destinoId, destino.getNombre(), creadas, preguntados, usuarios);
	}

	@Override
	public ResultadoPreguntaContinuidadDTO pedirConfirmacionSiHaySiguiente() {
		Temporada siguiente = temporadaSiguiente();

		if (siguiente == null) {
			logger.info("Confirmación de continuidad: no existe la temporada siguiente, no se hace nada");
			return null;
		}

		return pedirConfirmacion(siguiente.getId());
	}

	// ============================================================
	// Recordatorio semanal
	// ============================================================

	@Override
	@Transactional(readOnly = true)
	public int recordarPendientes() {
		Temporada siguiente = temporadaSiguiente();

		if (siguiente == null || siguiente.getFechaInicio() == null
				|| !LocalDate.now(clock).isBefore(aLocalDate(siguiente.getFechaInicio()))) {
			return 0;
		}

		Set<String> formativas = categoriasFormativas();
		Map<Long, List<JugadorTemporada>> porCuenta = new LinkedHashMap<>();

		for (JugadorTemporada fila : jugadorTemporadaDao.listarPorTemporada(siguiente.getId())) {
			if (fila.getFechaPreguntaContinuidad() == null || !esFormativoPendiente(fila, formativas)) {
				continue;
			}

			for (Long cuenta : cuentasDe(fila.getJugadorId())) {
				porCuenta.computeIfAbsent(cuenta, k -> new java.util.ArrayList<>()).add(fila);
			}
		}

		int avisadas = 0;
		for (Map.Entry<Long, List<JugadorTemporada>> e : porCuenta.entrySet()) {
			if (avisarFamilia(e.getKey(), e.getValue(), siguiente, true)) {
				avisadas++;
			}
		}

		logger.info("Recordatorio de continuidad: temporada={}, cuentasConPendientes={}, avisadas={}",
				siguiente.getNombre(), porCuenta.size(), avisadas);

		return avisadas;
	}

	/** Una notificación por cuenta con los nombres de todos sus jugadores. */
	private boolean avisarFamilia(Long cuenta, List<JugadorTemporada> filas, Temporada destino, boolean recordatorio) {
		List<String> nombres = filas.stream().map(f -> f.getJugadorNombre() == null ? "" : f.getJugadorNombre().trim())
				.toList();
		String ids = filas.stream().map(f -> String.valueOf(f.getJugadorId())).collect(Collectors.joining(","));

		String titulo = recordatorio ? "Recordatorio: continuidad en " + destino.getNombre()
				: "Continuidad en " + destino.getNombre();
		String pregunta = nombres.size() == 1 ? "¿Seguirá " + nombres.get(0) + " en la temporada " + destino.getNombre() + "?"
				: "¿Seguirán " + unir(nombres) + " en la temporada " + destino.getNombre() + "?";
		String mensaje = recordatorio ? "Aún no nos has contestado. " + pregunta : pregunta;

		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("pantalla", PANTALLA_FAMILIA);
		datos.put("accion", recordatorio ? "RECORDATORIO" : "PREGUNTA");
		datos.put("temporadaId", String.valueOf(destino.getId()));
		datos.put("jugadorIds", ids);

		return enviar(cuenta, TIPO_FAMILIA, titulo, mensaje, destino.getId(), datos);
	}

	private static String unir(List<String> nombres) {
		if (nombres.size() == 1) {
			return nombres.get(0);
		}

		return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.get(nombres.size() - 1);
	}

	// ============================================================
	// Respuestas desde la app
	// ============================================================

	@Override
	@Transactional(readOnly = true)
	public List<ContinuidadJugadorDTO> listar(Long usuarioAppId) {
		Temporada siguiente = temporadaSiguiente();

		if (siguiente == null || usuarioAppId == null) {
			return List.of();
		}

		List<ContinuidadJugadorDTO> resultado = new java.util.ArrayList<>();

		for (Jugador jugador : perfilAppDao.obtenerJugadoresPorUsuario(usuarioAppId.intValue())) {
			JugadorTemporada fila = jugadorTemporadaDao.obtener(jugador.getId(), siguiente.getId());

			if (debeConfirmar(fila)) {
				resultado.add(aDto(fila, siguiente));
			}
		}

		return resultado;
	}

	/** Solo los jugadores a los que se ha preguntado: los adultos y las pruebas no tienen que confirmar. */
	private static boolean debeConfirmar(JugadorTemporada fila) {
		return fila != null && fila.getFechaPreguntaContinuidad() != null && !"PRUEBA".equals(fila.getEstado());
	}

	private static ContinuidadJugadorDTO aDto(JugadorTemporada fila, Temporada temporada) {
		String nombre = ((fila.getJugadorNombre() == null ? "" : fila.getJugadorNombre()) + " "
				+ (fila.getJugadorApellidos() == null ? "" : fila.getJugadorApellidos())).trim();

		return new ContinuidadJugadorDTO(fila.getJugadorId(), nombre, fila.getCategoria(), temporada.getId(),
				temporada.getNombre(), fila.getContinuidad(), fila.getFechaContinuidad());
	}

	@Override
	@Transactional
	public ContinuidadJugadorDTO responder(Long usuarioAppId, Long jugadorId, String respuesta) {
		if (usuarioAppId == null || usuarioAppId <= 0 || jugadorId == null
				|| !vinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), jugadorId)) {
			throw new SecurityException("No tienes vinculado a este jugador.");
		}

		String limpia = respuesta == null ? "" : respuesta.trim().toUpperCase();
		if (!RESPUESTAS.contains(limpia)) {
			throw new IllegalArgumentException("La respuesta tiene que ser SIGUE, NO_SIGUE o DUDA.");
		}

		Temporada siguiente = siguienteObligatoria();
		JugadorTemporada fila = jugadorTemporadaDao.obtener(jugadorId, siguiente.getId());

		if (!debeConfirmar(fila)) {
			throw new IllegalArgumentException("Este jugador no tiene que confirmar la continuidad.");
		}

		String anterior = fila.getContinuidad();
		nuevaTemporadaService.marcarContinuidad(siguiente.getId(), jugadorId, limpia, usuarioAppId);

		if (!limpia.equals(anterior) && ("NO_SIGUE".equals(limpia) || "DUDA".equals(limpia))) {
			String nombre = aDto(fila, siguiente).nombre();
			String categoria = fila.getCategoria() == null ? "" : " (" + fila.getCategoria() + ")";
			despuesDelCommit(() -> avisarCoordinadores(jugadorId, nombre + categoria, siguiente, limpia));
		}

		JugadorTemporada guardada = jugadorTemporadaDao.obtener(jugadorId, siguiente.getId());

		return aDto(guardada == null ? fila : guardada, siguiente);
	}

	private void avisarCoordinadores(Long jugadorId, String jugador, Temporada destino, String respuesta) {
		boolean baja = "NO_SIGUE".equals(respuesta);
		String titulo = baja ? "No seguirá: " + jugador : "Duda sobre la continuidad: " + jugador;
		String mensaje = baja
				? "Su familia ha respondido que no seguirá en " + destino.getNombre() + ". Confirma la baja en el asistente."
				: "Su familia no sabe aún si seguirá en " + destino.getNombre() + ". Decide cuando lo sepas.";

		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("pantalla", PANTALLA_COORDINADOR);
		datos.put("temporadaId", String.valueOf(destino.getId()));
		datos.put("jugadorId", String.valueOf(jugadorId));
		datos.put("respuesta", respuesta);

		for (Long coordinador : equipoGestionDao.obtenerCoordinadores()) {
			enviar(coordinador, TIPO_COORDINADOR, titulo, mensaje, jugadorId, datos);
		}
	}

	// ============================================================
	// Altas de prueba
	// ============================================================

	@Override
	@Transactional
	public JugadorTemporada altaPrueba(Long usuarioAppId, String nombre, String apellidos, Integer anioNacimiento) {
		if (usuarioAppId == null || usuarioAppId <= 0 || !equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
			throw new SecurityException("Solo coordinación puede dar de alta jugadores de prueba");
		}

		return nuevaTemporadaService.anadirPrueba(siguienteObligatoria().getId(), nombre, apellidos, anioNacimiento);
	}

	// ============================================================
	// Envío
	// ============================================================

	/**
	 * Guarda la notificación en la bandeja de la cuenta y manda el push, si su cuenta acepta avisos de comunicaciones.
	 * Un fallo de envío no rompe la operación: se registra y se sigue con el resto.
	 */
	private boolean enviar(Long cuenta, String tipo, String titulo, String mensaje, Long referenciaId,
			Map<String, String> datos) {
		try {
			if (!notificacionAppService.puedeRecibir(cuenta, "COMUNICACION")) {
				return false;
			}

			notificacionAppService.crear(cuenta, tipo, titulo, mensaje, referenciaId);
			fcmPushService.enviarNotificacionAUsuario(cuenta, tipo, titulo, mensaje, referenciaId, datos);

			return true;

		} catch (RuntimeException e) {
			logger.error("No se ha podido avisar a la cuenta {} ({}): {}", cuenta, tipo, e.getMessage(), e);

			return false;
		}
	}

	/** Tras el commit si hay transacción; si no, al momento. */
	private static void despuesDelCommit(Runnable tarea) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					tarea.run();
				}
			});
			return;
		}

		tarea.run();
	}
}
