package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.CuentasSinJugadoresService;
import com.mikedev.mutxamelcf.service.PurgaComunicacionesService;

/**
 * Tareas nocturnas de mantenimiento ligadas al cambio de temporada, en hora de Madrid. Un fallo se registra y no
 * impide la siguiente ejecución ni la otra tarea.
 * <ul>
 * <li>03:30: purga de las comunicaciones de hace más de dos temporadas.</li>
 * <li>04:00: aviso y desactivación de las cuentas de familia sin ningún jugador activo.</li>
 * </ul>
 */
@Component
public class LimpiezaDeTemporadasScheduler {

	private static final Logger logger = LoggerFactory.getLogger(LimpiezaDeTemporadasScheduler.class);

	private final PurgaComunicacionesService purgaComunicaciones;
	private final CuentasSinJugadoresService cuentasSinJugadores;

	public LimpiezaDeTemporadasScheduler(PurgaComunicacionesService purgaComunicaciones,
			CuentasSinJugadoresService cuentasSinJugadores) {
		this.purgaComunicaciones = purgaComunicaciones;
		this.cuentasSinJugadores = cuentasSinJugadores;
	}

	@Scheduled(cron = "0 30 3 * * *", zone = "Europe/Madrid")
	public void purgarComunicaciones() {
		logger.info("Inicio job de purga de comunicaciones");

		try {
			purgaComunicaciones.purgar();
			logger.info("Fin job de purga de comunicaciones");
		} catch (RuntimeException e) {
			logger.error("Ha fallado la purga de comunicaciones: {}", e.getMessage(), e);
		}
	}

	@Scheduled(cron = "0 0 4 * * *", zone = "Europe/Madrid")
	public void revisarCuentasSinJugadores() {
		logger.info("Inicio job de cuentas sin jugadores activos");

		try {
			cuentasSinJugadores.procesar();
		} catch (RuntimeException e) {
			logger.error("Ha fallado el job de cuentas sin jugadores activos: {}", e.getMessage(), e);
		}
	}
}
