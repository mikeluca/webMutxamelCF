package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.ResultadoPreguntaContinuidadDTO;
import com.mikedev.mutxamelcf.service.ContinuidadService;

/**
 * Tareas de la confirmación de continuidad con las familias (docs/transicion-temporada.md, §5):
 * <ul>
 * <li>El 1 de mayo, si ya existe la temporada siguiente, genera el borrador y pregunta a las familias de cada jugador
 * formativo. Es idempotente: si se repite (o se pulsó antes el botón del asistente) no vuelve a avisar a nadie.</li>
 * <li>Cada lunes recuerda a las familias que todavía no han contestado, hasta que empieza la temporada nueva.</li>
 * </ul>
 * Los dos trabajos corren en hora de Madrid. Un fallo se registra y no impide la siguiente ejecución.
 */
@Component
public class ContinuidadScheduler {

	private static final Logger logger = LoggerFactory.getLogger(ContinuidadScheduler.class);

	private final ContinuidadService continuidadService;

	public ContinuidadScheduler(ContinuidadService continuidadService) {
		this.continuidadService = continuidadService;
	}

	/** El 1 de mayo a las 09:00. */
	@Scheduled(cron = "0 0 9 1 5 *", zone = "Europe/Madrid")
	public void pedirConfirmacion() {
		logger.info("Inicio job de confirmación de continuidad (1 de mayo)");

		try {
			ResultadoPreguntaContinuidadDTO resultado = continuidadService.pedirConfirmacionSiHaySiguiente();

			if (resultado != null) {
				logger.info("Fin job de confirmación de continuidad: temporada={}, preguntados={}, cuentas={}",
						resultado.temporada(), resultado.jugadoresPreguntados(), resultado.usuariosAvisados());
			}
		} catch (RuntimeException e) {
			logger.error("Ha fallado el job de confirmación de continuidad: {}", e.getMessage(), e);
		}
	}

	/** Los lunes a las 10:00. */
	@Scheduled(cron = "0 0 10 * * MON", zone = "Europe/Madrid")
	public void recordarPendientes() {
		logger.info("Inicio job de recordatorio de continuidad pendiente");

		try {
			int avisadas = continuidadService.recordarPendientes();
			logger.info("Fin job de recordatorio de continuidad: cuentasAvisadas={}", avisadas);
		} catch (RuntimeException e) {
			logger.error("Ha fallado el recordatorio de continuidad: {}", e.getMessage(), e);
		}
	}
}
