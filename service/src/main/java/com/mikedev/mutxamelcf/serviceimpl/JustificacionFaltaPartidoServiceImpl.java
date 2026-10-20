package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.JustificacionFaltaPartidoService;

@Service
public class JustificacionFaltaPartidoServiceImpl implements JustificacionFaltaPartidoService {

	private static final Logger logger = LoggerFactory.getLogger(JustificacionFaltaPartidoServiceImpl.class);

	static final int MOTIVO_MINIMO = 3;
	static final int MOTIVO_MAXIMO = 500;

	/** Como mucho un aviso por jugador y partido en este tiempo. */
	static final Duration INTERVALO_AVISOS = Duration.ofMinutes(10);

	/** Valor de data["tipo"] del push que lleva al entrenador a editar la convocatoria. */
	static final String TIPO_PUSH_CONVOCATORIA_EDITAR = "CONVOCATORIA_EDITAR";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter LIMITE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

	private final JustificacionFaltaPartidoDao justificacionDao;
	private final PartidoDao partidoDao;
	private final ConvocatoriaDao convocatoriaDao;
	private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
	private final EquipoGestionDao equipoGestionDao;
	private final UsuarioAppVinculoDao usuarioAppVinculoDao;
	private final ComunicacionService comunicacionService;
	private final Clock clock;
	private final LimitadorAvisos limitadorAvisos;

	public JustificacionFaltaPartidoServiceImpl(
			JustificacionFaltaPartidoDao justificacionDao,
			PartidoDao partidoDao,
			ConvocatoriaDao convocatoriaDao,
			ConvocatoriaJugadorDao convocatoriaJugadorDao,
			EquipoGestionDao equipoGestionDao,
			UsuarioAppVinculoDao usuarioAppVinculoDao,
			ComunicacionService comunicacionService,
			Clock clock) {

		this.justificacionDao = justificacionDao;
		this.partidoDao = partidoDao;
		this.convocatoriaDao = convocatoriaDao;
		this.convocatoriaJugadorDao = convocatoriaJugadorDao;
		this.equipoGestionDao = equipoGestionDao;
		this.usuarioAppVinculoDao = usuarioAppVinculoDao;
		this.comunicacionService = comunicacionService;
		this.clock = clock;
		this.limitadorAvisos = new LimitadorAvisos(clock, INTERVALO_AVISOS);
	}

	// ------------------------------------------------------------------
	// Justificar
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public JustificacionFaltaPartidoResponse justificar(
			Long usuarioAppId, Long partidoId, JustificacionFaltaRequest request) {

		validarUsuario(usuarioAppId);

		if (partidoId == null || partidoId <= 0) {
			throw new IllegalArgumentException("El partido no es válido");
		}

		if (request == null || request.getJugadorId() == null) {
			throw new IllegalArgumentException("El jugador es obligatorio");
		}

		Long jugadorId = request.getJugadorId();

		// Serializa con otras justificaciones y con la edición de la convocatoria de este partido.
		partidoDao.bloquearPorId(partidoId);

		Partido partido = validarPuedeCambiar(usuarioAppId, partidoId, jugadorId);

		String motivo = validarMotivo(request.getMotivo());

		Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partidoId);

		validarPlazo(partido, convocatoria);

		LocalDateTime ahora = LocalDateTime.now(clock);

		JustificacionFaltaPartido existente = justificacionDao.obtenerPorPartidoYJugador(partidoId, jugadorId);

		if (existente != null) {

			// Solo cambia el motivo: se guarda sin nuevo aviso.
			existente.setMotivo(motivo);
			existente.setUsuarioAppId(usuarioAppId);
			existente.setFechaCreacion(ahora);

			justificacionDao.actualizar(existente);

			logger.info("Ausencia a partido actualizada: partidoId={}, jugadorId={}", partidoId, jugadorId);

			return construirResponse(existente);
		}

		JustificacionFaltaPartido justificacion = new JustificacionFaltaPartido();

		justificacion.setPartidoId(partidoId);
		justificacion.setJugadorId(jugadorId);
		justificacion.setUsuarioAppId(usuarioAppId);
		justificacion.setMotivo(motivo);
		justificacion.setFechaCreacion(ahora);

		justificacion = justificacionDao.crear(justificacion);

		// Privacidad: ni el motivo ni nombres en el log.
		logger.info("Ausencia a partido justificada: partidoId={}, jugadorId={}", partidoId, jugadorId);

		avisarJustificacion(partido, convocatoria, jugadorId, motivo, usuarioAppId);

		return construirResponse(justificacion);
	}

	/*
	 * Avisos al cuerpo técnico tras una justificación NUEVA. El motivo va solo
	 * en la comunicación (dentro de la app); el push, que se ve en la pantalla
	 * bloqueada, no lo lleva.
	 */
	private void avisarJustificacion(
			Partido partido, Convocatoria convocatoria, Long jugadorId, String motivo, Long usuarioAppId) {

		String jugador = equipoGestionDao.obtenerNombreJugador(jugadorId);
		String rival = partido.getRival();

		boolean convocado = convocatoria != null
				&& convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(convocatoria.getId(), jugadorId) != null;

		if (!convocado) {

			if (repetido(partido.getId(), jugadorId, TipoAviso.JUSTIFICADA)) {
				return;
			}

			String fecha = textoFecha(partido);

			enviar(partido.getEquipoId(), usuarioAppId,
					"Ausencia justificada",
					jugador + " no podrá jugar el partido contra " + rival + " (" + fecha + "). Ausencia justificada."
							+ " Motivo: " + motivo,
					jugador + " no podrá jugar el partido contra " + rival + " (" + fecha + "). Ausencia justificada.",
					Map.of());

			registrarAvisoTrasCommit(partido.getId(), jugadorId, TipoAviso.JUSTIFICADA);
			return;
		}

		if (hayDisponibles(partido, convocatoria)) {

			if (repetido(partido.getId(), jugadorId, TipoAviso.CONVOCADO_SUSTITUIR)) {
				return;
			}

			String texto = jugador + " está convocado y ha justificado su ausencia para el partido contra " + rival
					+ ". Edita la convocatoria para sustituirlo.";

			enviar(partido.getEquipoId(), usuarioAppId,
					"Convocado con ausencia justificada",
					texto + " Motivo: " + motivo,
					texto,
					Map.of("tipo", TIPO_PUSH_CONVOCATORIA_EDITAR,
							"convocatoriaId", String.valueOf(convocatoria.getId())));

			registrarAvisoTrasCommit(partido.getId(), jugadorId, TipoAviso.CONVOCADO_SUSTITUIR);
			return;
		}

		// Nadie con quien sustituirlo: se le quita de la convocatoria. Este aviso se envía siempre.
		convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(convocatoria.getId(), jugadorId);

		String texto = jugador + " ha justificado su ausencia y se ha quitado de la convocatoria del partido contra "
				+ rival + " (no quedan más jugadores disponibles).";

		enviar(partido.getEquipoId(), usuarioAppId,
				"Quitado de la convocatoria",
				texto + " Motivo: " + motivo,
				texto,
				Map.of());

		registrarAvisoTrasCommit(partido.getId(), jugadorId, TipoAviso.QUITADO);
	}

	/*
	 * Disponibles = jugadores del equipo que no están en la convocatoria y no
	 * tienen justificación para este partido.
	 */
	private boolean hayDisponibles(Partido partido, Convocatoria convocatoria) {

		Set<Long> ocupados = new HashSet<>();

		for (ConvocatoriaJugador convocado : convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoria.getId())) {
			ocupados.add(convocado.getJugadorId());
		}

		for (JustificacionFaltaPartido justificada : justificacionDao.obtenerPorPartido(partido.getId())) {
			ocupados.add(justificada.getJugadorId());
		}

		return equipoGestionDao.obtenerJugadoresPorEquipo(partido.getEquipoId()).stream()
				.anyMatch(id -> !ocupados.contains(id));
	}

	// ------------------------------------------------------------------
	// Retirar
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public void retirar(Long usuarioAppId, Long partidoId, Long jugadorId) {

		validarUsuario(usuarioAppId);

		if (partidoId == null || partidoId <= 0) {
			throw new IllegalArgumentException("El partido no es válido");
		}

		if (jugadorId == null || jugadorId <= 0) {
			throw new IllegalArgumentException("El jugador es obligatorio");
		}

		partidoDao.bloquearPorId(partidoId);

		Partido partido = validarPuedeCambiar(usuarioAppId, partidoId, jugadorId);

		validarPlazo(partido, convocatoriaDao.obtenerPorPartidoId(partidoId));

		if (justificacionDao.eliminar(partidoId, jugadorId) == 0) {
			throw new IllegalArgumentException("El jugador no tiene una ausencia justificada para este partido");
		}

		logger.info("Ausencia a partido retirada: partidoId={}, jugadorId={}", partidoId, jugadorId);

		if (repetido(partidoId, jugadorId, TipoAviso.RETIRADA)) {
			return;
		}

		String jugador = equipoGestionDao.obtenerNombreJugador(jugadorId);
		String texto = jugador + " ya puede jugar el partido contra " + partido.getRival()
				+ "; no se ha añadido a la convocatoria.";

		enviar(partido.getEquipoId(), usuarioAppId, "Ausencia retirada", texto, texto, Map.of());

		registrarAvisoTrasCommit(partidoId, jugadorId, TipoAviso.RETIRADA);
	}

	// ------------------------------------------------------------------
	// Consulta
	// ------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<JustificacionFaltaPartidoResponse> obtener(Long usuarioAppId, Long partidoId) {

		validarUsuario(usuarioAppId);

		if (partidoId == null || partidoId <= 0) {
			throw new IllegalArgumentException("El partido no es válido");
		}

		Partido partido = partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, partido.getEquipoId())) {
			throw new SecurityException("No tienes permiso para consultar las ausencias de este equipo");
		}

		List<JustificacionFaltaPartidoResponse> respuesta = new ArrayList<>();

		for (JustificacionFaltaPartido justificacion : justificacionDao.obtenerPorPartido(partidoId)) {
			respuesta.add(construirResponse(justificacion));
		}

		return respuesta;
	}

	// ------------------------------------------------------------------
	// Validaciones
	// ------------------------------------------------------------------

	/*
	 * Comprueba lo común a justificar y retirar. El vínculo va primero: quien
	 * no lo tiene recibe 403 sea cual sea el resto.
	 */
	private Partido validarPuedeCambiar(Long usuarioAppId, Long partidoId, Long jugadorId) {

		Partido partido = partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		if (!usuarioAppVinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), jugadorId)) {
			throw new SecurityException("No tienes permiso para justificar la ausencia de este jugador");
		}

		if (!equipoGestionDao.perteneceJugadorAEquipo(jugadorId, partido.getEquipoId())) {
			throw new IllegalArgumentException("El jugador no pertenece al equipo de este partido");
		}

		if (partido.isCancelado()) {
			throw new IllegalArgumentException("El partido está cancelado");
		}

		return partido;
	}

	private void validarPlazo(Partido partido, Convocatoria convocatoria) {

		LocalDateTime limite = PlazoJustificacionPartido.limite(partido, convocatoria);

		if (limite == null || LocalDateTime.now(clock).isAfter(limite)) {
			throw new IllegalArgumentException(limite == null
					? "El partido no tiene fecha: no se puede justificar la ausencia"
					: "El plazo para justificar la ausencia terminó el " + LIMITE.format(limite));
		}
	}

	private static String validarMotivo(String texto) {

		String motivo = texto == null ? "" : texto.trim();

		if (motivo.length() < MOTIVO_MINIMO || motivo.length() > MOTIVO_MAXIMO) {
			throw new IllegalArgumentException("El motivo es obligatorio y debe tener entre " + MOTIVO_MINIMO
					+ " y " + MOTIVO_MAXIMO + " caracteres");
		}

		return motivo;
	}

	private static void validarUsuario(Long usuarioAppId) {

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}
	}

	// ------------------------------------------------------------------
	// Avisos
	// ------------------------------------------------------------------

	/** Qué aviso se manda al cuerpo técnico; el límite de 10 minutos es por tipo. */
	enum TipoAviso {
		JUSTIFICADA, CONVOCADO_SUSTITUIR, QUITADO, RETIRADA
	}

	private static String claveAviso(Long partidoId, Long jugadorId) {
		return partidoId + ":" + jugadorId;
	}

	/*
	 * true si el último aviso de este jugador y partido fue del MISMO tipo hace
	 * menos de 10 minutos. Un cambio de estado (justificar -> retirar, etc.)
	 * se avisa siempre.
	 */
	private boolean repetido(Long partidoId, Long jugadorId, TipoAviso tipo) {
		return !limitadorAvisos.permitir(claveAviso(partidoId, jugadorId), tipo.name());
	}

	/*
	 * El envío se anota en el limitador cuando la transacción ha confirmado: si
	 * se deshace, no consta ningún aviso enviado.
	 */
	private void registrarAvisoTrasCommit(Long partidoId, Long jugadorId, TipoAviso tipo) {

		Runnable registrar = () -> limitadorAvisos.registrar(claveAviso(partidoId, jugadorId), tipo.name());

		if (TransactionSynchronizationManager.isSynchronizationActive()) {

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					registrar.run();
				}
			});

		} else {

			registrar.run();
		}
	}

	/** Solo para tests: el tipo del último aviso registrado para ese partido y jugador. */
	String ultimoTipoAviso(Long partidoId, Long jugadorId) {
		return limitadorAvisos.ultimoTipo(claveAviso(partidoId, jugadorId));
	}

	private String textoFecha(Partido partido) {

		LocalDate dia = PlazoJustificacionPartido.dia(partido);

		return dia == null ? "sin fecha" : FECHA.format(dia);
	}

	private void enviar(
			Long equipoId, Long usuarioAppId, String titulo, String contenido, String contenidoPush,
			Map<String, String> datosPush) {

		List<Long> entrenadores = new ArrayList<>(equipoGestionDao.obtenerEntrenadoresPorEquipo(equipoId));

		// Sin ningún entrenador con cuenta, el aviso va a los coordinadores.
		if (entrenadores.isEmpty()) {

			entrenadores.addAll(equipoGestionDao.obtenerCoordinadores());

			if (entrenadores.isEmpty()) {
				logger.warn("Aviso de ausencia sin destinatarios: el equipo {} no tiene entrenadores ni hay coordinadores",
						equipoId);
				return;
			}
		}

		// Quien justifica puede ser también del cuerpo técnico: no se avisa a sí mismo.
		entrenadores.remove(usuarioAppId);

		if (entrenadores.isEmpty()) {
			return;
		}

		Comunicacion comunicacion = new Comunicacion();
		comunicacion.setTitulo(titulo);
		comunicacion.setContenido(contenido);

		comunicacionService.crearPrivada(comunicacion, entrenadores, usuarioAppId,
				new OpcionesPushComunicacion(contenidoPush, datosPush));
	}

	private JustificacionFaltaPartidoResponse construirResponse(JustificacionFaltaPartido justificacion) {

		return new JustificacionFaltaPartidoResponse(
				justificacion.getPartidoId(),
				justificacion.getJugadorId(),
				equipoGestionDao.obtenerNombreJugador(justificacion.getJugadorId()),
				justificacion.getMotivo(),
				justificacion.getFechaCreacion());
	}
}
