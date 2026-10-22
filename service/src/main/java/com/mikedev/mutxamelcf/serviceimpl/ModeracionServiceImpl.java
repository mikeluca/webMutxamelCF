package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mikedev.mutxamelcf.dao.ComunicacionDao;
import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ReporteComunicacionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ReporteComunicacion;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse.Autor;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse.Persona;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.ModeracionService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@Service
public class ModeracionServiceImpl implements ModeracionService {

	private static final Logger logger = LoggerFactory.getLogger(ModeracionServiceImpl.class);

	static final String TIPO_PUSH_REPORTE = "REPORTE";
	static final int MAXIMO_RESUELTOS = 50;
	static final int MAXIMO_NOTA = 500;
	static final int MAXIMO_TEXTO_COPIA = 300;

	private final ReporteComunicacionDao reporteDao;
	private final ComunicacionDao comunicacionDao;
	private final ComunicacionService comunicacionService;
	private final UsuarioAppService usuarioAppService;
	private final EquipoGestionDao equipoGestionDao;
	private final DispositivoAppDao dispositivoAppDao;
	private final FcmPushService fcmPushService;
	private final Clock clock;

	public ModeracionServiceImpl(
			ReporteComunicacionDao reporteDao,
			ComunicacionDao comunicacionDao,
			ComunicacionService comunicacionService,
			UsuarioAppService usuarioAppService,
			EquipoGestionDao equipoGestionDao,
			DispositivoAppDao dispositivoAppDao,
			FcmPushService fcmPushService,
			Clock clock) {

		this.reporteDao = reporteDao;
		this.comunicacionDao = comunicacionDao;
		this.comunicacionService = comunicacionService;
		this.usuarioAppService = usuarioAppService;
		this.equipoGestionDao = equipoGestionDao;
		this.dispositivoAppDao = dispositivoAppDao;
		this.fcmPushService = fcmPushService;
		this.clock = clock;
	}

	// ---- registrar --------------------------------------------------------------------------

	@Override
	@Transactional
	public Long registrarReporte(Comunicacion comunicacion, Long reportadorId, String motivo) {

		if (comunicacion == null || comunicacion.getId() == null || reportadorId == null) {
			throw new IllegalArgumentException("Faltan datos del reporte");
		}

		if (reporteDao.existe(comunicacion.getId(), reportadorId)) {
			return null;
		}

		LocalDateTime fechaMensaje = comunicacion.getFechaPublicacion() != null
				? comunicacion.getFechaPublicacion()
				: comunicacion.getFechaCreacion();

		Long reporteId;

		try {

			reporteId = reporteDao.crear(new ReporteComunicacion(
					null, comunicacion.getId(), comunicacion.getUsuarioAutorId(), reportadorId,
					recortar(motivo, 500), recortar(comunicacion.getTitulo(), MAXIMO_TEXTO_COPIA),
					comunicacion.getContenido(), fechaMensaje, LocalDateTime.now(clock),
					ReporteComunicacion.PENDIENTE, null, null, null));

		} catch (DuplicateKeyException e) {

			// Dos peticiones a la vez del mismo usuario: gana la primera.
			return null;
		}

		logger.info("Mensaje reportado: reporteId={}, comunicacionId={}, reportadorId={}",
				reporteId, comunicacion.getId(), reportadorId);

		despuesDelCommit(() -> avisarAAdmins(reporteId, reportadorId));

		return reporteId;
	}

	/** Push a los ADMIN_APP: sin el contenido del mensaje. */
	private void avisarAAdmins(Long reporteId, Long reportadorId) {

		for (Long adminId : equipoGestionDao.obtenerAdministradoresApp()) {

			if (adminId.equals(reportadorId)) {
				continue;
			}

			try {

				fcmPushService.enviarNotificacionAUsuario(
						adminId, TIPO_PUSH_REPORTE, "Nuevo mensaje reportado",
						"Hay un mensaje pendiente de revisar en Moderación.", reporteId,
						Map.of("reporteId", String.valueOf(reporteId)));

			} catch (Exception e) {

				logger.error("No se ha podido avisar al administrador {} del reporte {}: {}",
						adminId, reporteId, e.getMessage());
			}
		}
	}

	// ---- consultar --------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<ReporteModeracionResponse> listar(Long moderadorId, String estado) {

		validarAdmin(moderadorId);

		boolean resueltos = estado != null && "RESUELTOS".equalsIgnoreCase(estado.trim());

		if (estado != null && !estado.isBlank() && !resueltos && !"PENDIENTE".equalsIgnoreCase(estado.trim())) {
			throw new IllegalArgumentException("El estado debe ser PENDIENTE o RESUELTOS");
		}

		List<ReporteComunicacion> reportes = resueltos
				? reporteDao.listarResueltos(MAXIMO_RESUELTOS)
				: reporteDao.listarPendientes();

		// Los administradores se obtienen UNA vez por listado y autorEsAdmin se calcula en memoria.
		java.util.Set<Long> administradores = new java.util.HashSet<>(equipoGestionDao.obtenerAdministradoresApp());

		return reportes.stream().map(r -> aRespuesta(r, administradores)).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public int contarPendientes(Long moderadorId) {
		validarAdmin(moderadorId);

		return reporteDao.contarPendientes();
	}

	// ---- acciones ---------------------------------------------------------------------------

	@Override
	@Transactional
	public void descartar(Long moderadorId, Long reporteId, String nota) {

		validarAdmin(moderadorId);

		ReporteComunicacion reporte = pendiente(reporteId);

		resolver(reporte, ReporteComunicacion.DESCARTADO, moderadorId, nota);

		auditar(reporteId, "DESCARTAR", moderadorId);
	}

	@Override
	@Transactional
	public void borrarMensaje(Long moderadorId, Long reporteId, String nota) {

		validarAdmin(moderadorId);

		ReporteComunicacion reporte = pendiente(reporteId);

		borrarComunicacion(reporte, moderadorId);

		resolverTodosDelMensaje(reporte, ReporteComunicacion.MENSAJE_BORRADO, moderadorId, nota);

		auditar(reporteId, "BORRAR_MENSAJE", moderadorId);
	}

	@Override
	@Transactional
	public void desactivarAutor(Long moderadorId, Long reporteId, String nota, boolean borrarMensaje) {

		validarAdmin(moderadorId);

		ReporteComunicacion reporte = pendiente(reporteId);

		Long autorId = reporte.autorId();

		if (autorId == null) {
			throw new IllegalArgumentException("El mensaje no tiene un autor al que desactivar");
		}

		if (autorId.equals(moderadorId)) {
			throw new IllegalArgumentException("No puedes desactivar tu propia cuenta");
		}

		UsuarioApp autor = usuarioAppService.obtenerPorId(autorId.intValue());

		if (autor == null) {
			throw new IllegalArgumentException("El autor ya no existe");
		}

		if (usuarioAppService.tieneRol(autorId.intValue(), "ADMIN_APP")) {
			throw new IllegalArgumentException("No se puede desactivar a un administrador");
		}

		// La misma lógica que /admin/usuarios-app/{id}/desactivar del panel web (ACTIVO = 0:
		// el filtro JWT rechaza desde ese momento sus tokens) y, además, sus dispositivos FCM
		// se dan de baja para que no reciba más pushes.
		usuarioAppService.desactivarUsuarioAdmin(autorId.intValue());
		dispositivoAppDao.eliminarTodosDeUsuario(autorId);

		if (borrarMensaje) {
			borrarComunicacion(reporte, moderadorId);
			resolverTodosDelMensaje(reporte, ReporteComunicacion.AUTOR_DESACTIVADO, moderadorId, nota);
		} else {
			resolver(reporte, ReporteComunicacion.AUTOR_DESACTIVADO, moderadorId, nota);
		}

		auditar(reporteId, borrarMensaje ? "DESACTIVAR_AUTOR_Y_BORRAR_MENSAJE" : "DESACTIVAR_AUTOR", moderadorId);
	}

	@Override
	@Transactional
	public Long reactivarAutor(Long moderadorId, Long reporteId, String nota) {

		validarAdmin(moderadorId);

		if (reporteId == null) {
			throw new IllegalArgumentException("El reporte no es válido");
		}

		ReporteComunicacion reporte = reporteDao.obtenerPorId(reporteId);

		if (reporte == null) {
			throw new NoSuchElementException("El reporte no existe");
		}

		if (!ReporteComunicacion.AUTOR_DESACTIVADO.equals(reporte.estado())) {
			throw new IllegalStateException("El autor de este reporte no fue desactivado");
		}

		Long autorId = reporte.autorId();

		UsuarioApp autor = autorId == null ? null : usuarioAppService.obtenerPorId(autorId.intValue());

		if (autor == null) {
			throw new IllegalStateException("El autor ya no existe");
		}

		if (autor.isActivo()) {
			throw new IllegalStateException("La cuenta del autor ya está activa");
		}

		String notaLimpia = limpiarNota(nota);

		// La misma lógica que /admin/usuarios-app/{id}/activar del panel web. Los dispositivos no
		// se reactivan solos: se registran al volver a entrar.
		usuarioAppService.activarUsuarioAdmin(autorId.intValue());

		LocalDateTime ahora = LocalDateTime.now(clock);

		String nombreModerador = nombreDe(moderadorId);

		String anotacion = "Reactivada por "
				+ (nombreModerador == null || nombreModerador.isBlank() ? "el administrador " + moderadorId
						: nombreModerador)
				+ " el " + ahora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
				+ (notaLimpia == null ? "" : ": " + notaLimpia);

		String existente = reporte.notaResolucion();

		String completa = existente == null || existente.isBlank() ? anotacion : existente + "\n" + anotacion;

		reporteDao.actualizarNotaResolucion(reporteId, recortar(completa, MAXIMO_NOTA));

		auditar(reporteId, "REACTIVAR_AUTOR", moderadorId);

		return autorId;
	}

	// ---- apoyo ------------------------------------------------------------------------------

	private void borrarComunicacion(ReporteComunicacion reporte, Long moderadorId) {

		/*
		 * Se comprueba antes si existe en vez de capturar la excepción de eliminar():
		 * su @Transactional marcaría esta transacción como rollback-only y el commit
		 * acabaría en UnexpectedRollbackException.
		 */
		if (comunicacionDao.obtenerPorId(reporte.comunicacionId()) == null) {

			// Ya estaba borrado (por su autor, por otro moderador...): el objetivo está cumplido.
			logger.warn("El mensaje del reporte {} ya no existe: no hay nada que borrar", reporte.id());
			return;
		}

		comunicacionService.eliminar(reporte.comunicacionId(), moderadorId);
	}

	private void resolver(ReporteComunicacion reporte, String estado, Long moderadorId, String nota) {

		int filas = reporteDao.resolver(
				reporte.id(), estado, moderadorId, LocalDateTime.now(clock), limpiarNota(nota));

		if (filas == 0) {
			throw new IllegalStateException("El reporte ya estaba resuelto");
		}
	}

	/** El reporte indicado y los demás pendientes del mismo mensaje, con el mismo estado y nota. */
	private void resolverTodosDelMensaje(ReporteComunicacion reporte, String estado, Long moderadorId, String nota) {

		int filas = reporteDao.resolverPendientesDeComunicacion(
				reporte.comunicacionId(), estado, moderadorId, LocalDateTime.now(clock), limpiarNota(nota));

		if (filas == 0) {
			throw new IllegalStateException("El reporte ya estaba resuelto");
		}
	}

	private ReporteComunicacion pendiente(Long reporteId) {

		if (reporteId == null) {
			throw new IllegalArgumentException("El reporte no es válido");
		}

		ReporteComunicacion reporte = reporteDao.obtenerPorId(reporteId);

		if (reporte == null) {
			throw new NoSuchElementException("El reporte no existe");
		}

		if (!ReporteComunicacion.PENDIENTE.equals(reporte.estado())) {
			throw new IllegalStateException("El reporte ya estaba resuelto");
		}

		return reporte;
	}

	private void validarAdmin(Long usuarioId) {

		if (usuarioId == null || usuarioId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		if (!usuarioAppService.tieneRol(usuarioId.intValue(), "ADMIN_APP")) {
			throw new SecurityException("Solo un administrador puede moderar");
		}
	}

	private ReporteModeracionResponse aRespuesta(ReporteComunicacion r, java.util.Set<Long> administradores) {

		Autor autor = r.autorId() == null
				? new Autor(null, null, false, false)
				: new Autor(r.autorId(), nombreDe(r.autorId()), estaActivo(r.autorId()),
						administradores.contains(r.autorId()));

		return new ReporteModeracionResponse(
				r.id(), r.comunicacionId(), r.fechaReporte(), r.motivo(),
				new Persona(r.reportadorId(), nombreDe(r.reportadorId())), autor,
				r.tituloCopia(), r.contenidoCopia(), r.fechaMensaje(), r.estado(),
				r.resueltoPorId() == null ? null : new Persona(r.resueltoPorId(), nombreDe(r.resueltoPorId())),
				r.fechaResolucion(), r.notaResolucion(),
				reporteDao.contarMensajesReportadosDelAutor(r.autorId(), r.comunicacionId()));
	}

	private String nombreDe(Long usuarioId) {
		try {
			return usuarioAppService.obtenerNombreMostrable(usuarioId.intValue());
		} catch (RuntimeException e) {
			return null;
		}
	}

	private boolean estaActivo(Long usuarioId) {
		UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioId.intValue());

		return usuario != null && usuario.isActivo();
	}

	private static String limpiarNota(String nota) {

		if (nota == null || nota.isBlank()) {
			return null;
		}

		String limpia = nota.trim();

		if (limpia.length() > MAXIMO_NOTA) {
			throw new IllegalArgumentException("La nota no puede superar los " + MAXIMO_NOTA + " caracteres");
		}

		return limpia;
	}

	private static String recortar(String texto, int maximo) {

		if (texto == null) {
			return null;
		}

		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}

	private static void auditar(Long reporteId, String accion, Long moderadorId) {
		// Solo ids: nunca el contenido del mensaje.
		logger.info("Moderación: reporteId={}, accion={}, moderadorId={}", reporteId, accion, moderadorId);
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
