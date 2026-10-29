package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.CuentaSinJugadoresDao;
import com.mikedev.mutxamelcf.model.CuentaSinJugadores;
import com.mikedev.mutxamelcf.service.AvisosCuentaEmail;
import com.mikedev.mutxamelcf.service.CuentasSinJugadoresService;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

/**
 * Sin transacción común: cada cuenta se resuelve por separado y un fallo en una no impide tratar las demás.
 *
 * <p>
 * Una cuenta desactivada a mano por un administrador (ACTIVO = 0 sin la marca del job) nunca se reactiva sola; si tenía
 * un aviso pendiente, el aviso se borra para que no se desactive "otra vez" si luego la reactivan.
 */
@Service
public class CuentasSinJugadoresServiceImpl implements CuentasSinJugadoresService {

	private static final Logger logger = LoggerFactory.getLogger(CuentasSinJugadoresServiceImpl.class);

	/** Valor de {@code tipo} de la notificación y del push. */
	static final String TIPO = "CUENTA_SIN_JUGADORES";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CuentaSinJugadoresDao dao;
	private final NotificacionAppService notificacionAppService;
	private final FcmPushService fcmPushService;
	private final AvisosCuentaEmail email;
	private final Clock clock;

	public CuentasSinJugadoresServiceImpl(CuentaSinJugadoresDao dao, NotificacionAppService notificacionAppService,
			FcmPushService fcmPushService, AvisosCuentaEmail email, Clock clock) {
		this.dao = dao;
		this.notificacionAppService = notificacionAppService;
		this.fcmPushService = fcmPushService;
		this.email = email;
		this.clock = clock;
	}

	@Override
	public ResultadoCuentasSinJugadores procesar() {
		LocalDateTime ahora = LocalDateTime.now(clock);
		int avisadas = 0;
		int desactivadas = 0;
		int reactivadas = 0;
		int canceladas = 0;

		Set<Long> sinJugadores = new HashSet<>();

		for (CuentaSinJugadores cuenta : dao.listarSinJugadoresActivos(ahora.toLocalDate())) {
			sinJugadores.add(cuenta.id());

			try {
				switch (accion(cuenta, ahora)) {
				case AVISAR -> avisadas += avisar(cuenta, ahora) ? 1 : 0;
				case DESACTIVAR -> desactivadas += desactivar(cuenta) ? 1 : 0;
				case CANCELAR -> canceladas += dao.cancelarAviso(cuenta.id());
				case NADA -> {
				}
				}
			} catch (RuntimeException e) {
				logger.error("No se ha podido tratar la cuenta {} sin jugadores activos: {}", cuenta.id(), e.getMessage(), e);
			}
		}

		// Las que tienen marca pero ya no están en la lista: han vuelto a tener un jugador activo
		for (CuentaSinJugadores cuenta : dao.listarMarcadas()) {
			if (sinJugadores.contains(cuenta.id())) {
				continue;
			}

			try {
				if (cuenta.desactivadaPorEsto() && !cuenta.activa()) {
					if (dao.reactivar(cuenta.id()) > 0) {
						reactivadas++;
						logger.info("Cuenta {} reactivada: vuelve a tener un jugador activo", cuenta.id());
					}
				} else if (dao.cancelarAviso(cuenta.id()) > 0) {
					canceladas++;
					logger.info("Cuenta {}: aviso de desactivación cancelado, vuelve a tener un jugador activo", cuenta.id());
				}
			} catch (RuntimeException e) {
				logger.error("No se ha podido restablecer la cuenta {}: {}", cuenta.id(), e.getMessage(), e);
			}
		}

		logger.info("Cuentas sin jugadores activos: avisadas={}, desactivadas={}, reactivadas={}, avisosCancelados={}",
				avisadas, desactivadas, reactivadas, canceladas);

		return new ResultadoCuentasSinJugadores(avisadas, desactivadas, reactivadas, canceladas);
	}

	private enum Accion {
		AVISAR, DESACTIVAR, CANCELAR, NADA
	}

	private static Accion accion(CuentaSinJugadores cuenta, LocalDateTime ahora) {
		if (!cuenta.activa()) {
			// Desactivada por el job: espera a que vuelva un jugador. Desactivada a mano, o aún sin activar: no es cosa
			// del job, y un aviso antiguo no debe sobrevivir a la decisión del administrador
			return !cuenta.desactivadaPorEsto() && cuenta.fechaAviso() != null ? Accion.CANCELAR : Accion.NADA;
		}

		if (cuenta.desactivadaPorEsto()) {
			// Un administrador la ha reactivado a mano: se empieza de cero
			return Accion.CANCELAR;
		}

		if (cuenta.fechaAviso() == null) {
			return Accion.AVISAR;
		}

		return !ahora.isBefore(fechaDesactivacion(cuenta.fechaAviso()).atStartOfDay()) ? Accion.DESACTIVAR : Accion.NADA;
	}

	private static LocalDate fechaDesactivacion(Timestamp aviso) {
		return aviso.toLocalDateTime().toLocalDate().plusMonths(MESES_HASTA_DESACTIVAR);
	}

	/** Guarda primero la fecha (así, aunque falle el envío, no se avisa cada día) y luego manda push y email. */
	private boolean avisar(CuentaSinJugadores cuenta, LocalDateTime ahora) {
		Timestamp fecha = Timestamp.valueOf(ahora);

		if (dao.marcarAviso(cuenta.id(), fecha) == 0) {
			return false;
		}

		LocalDate desactivacion = fechaDesactivacion(fecha);
		String titulo = "Tu cuenta se desactivará";
		String mensaje = "No tienes ningún jugador activo vinculado a tu cuenta. Se desactivará el "
				+ FECHA.format(desactivacion) + " si no vuelve a haber ninguno. Si es un error, escribe al club.";

		try {
			if (notificacionAppService.puedeRecibir(cuenta.id(), "COMUNICACION")) {
				Map<String, String> datos = new LinkedHashMap<>();
				datos.put("fechaDesactivacion", desactivacion.toString());

				notificacionAppService.crear(cuenta.id(), TIPO, titulo, mensaje, cuenta.id());
				fcmPushService.enviarNotificacionAUsuario(cuenta.id(), TIPO, titulo, mensaje, cuenta.id(), datos);
			}
		} catch (RuntimeException e) {
			logger.error("No se ha podido enviar el push a la cuenta {}: {}", cuenta.id(), e.getMessage(), e);
		}

		if (cuenta.email() != null && !cuenta.email().isBlank()) {
			try {
				email.enviarAvisoCuentaSinJugadores(cuenta.email(), cuenta.nombre(), desactivacion);
			} catch (RuntimeException e) {
				logger.error("No se ha podido enviar el email a la cuenta {}: {}", cuenta.id(), e.getMessage(), e);
			}
		}

		logger.info("Cuenta {} sin jugadores activos: avisada, se desactivará el {}", cuenta.id(), desactivacion);

		return true;
	}

	private boolean desactivar(CuentaSinJugadores cuenta) {
		boolean desactivada = dao.desactivar(cuenta.id()) > 0;

		if (desactivada) {
			logger.info("Cuenta {} desactivada: {} meses sin ningún jugador activo", cuenta.id(), MESES_HASTA_DESACTIVAR);
		}

		return desactivada;
	}
}
