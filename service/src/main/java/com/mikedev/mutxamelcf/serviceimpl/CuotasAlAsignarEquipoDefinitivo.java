package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService;

/**
 * Al asignar el equipo definitivo se generan las cuotas del jugador (decisión 26): nunca a un jugador de prueba ni en
 * un equipo de pretemporada. Corre dentro de la transacción del reparto.
 */
@Component
public class CuotasAlAsignarEquipoDefinitivo implements AlAsignarEquipoDefinitivo {

	private static final Logger logger = LoggerFactory.getLogger(CuotasAlAsignarEquipoDefinitivo.class);

	private static final String PRETEMPORADA = "PRETEMPORADA";

	private final CuotasTemporadaService cuotasTemporadaService;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final EquipoDao equipoDao;

	public CuotasAlAsignarEquipoDefinitivo(CuotasTemporadaService cuotasTemporadaService,
			JugadorTemporadaDao jugadorTemporadaDao, EquipoDao equipoDao) {
		this.cuotasTemporadaService = cuotasTemporadaService;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.equipoDao = equipoDao;
	}

	@Override
	public void alAsignar(AsignacionDefinitiva asignacion) {
		JugadorTemporada ficha = jugadorTemporadaDao.obtener(asignacion.jugadorId(), asignacion.temporadaId());

		if (ficha == null || "PRUEBA".equals(ficha.getEstado()) || ficha.isBaja() || !ficha.isEquipoDefinitivo()) {
			logger.info("Sin cuotas: el jugador {} no es un jugador confirmado con equipo definitivo",
					asignacion.jugadorId());
			return;
		}

		if (esDePretemporada(asignacion.equipoId())) {
			logger.info("Sin cuotas: el equipo {} es de pretemporada", asignacion.equipoId());
			return;
		}

		cuotasTemporadaService.crearCuotasDelJugador(asignacion.jugadorId());
	}

	private boolean esDePretemporada(Long equipoId) {
		try {
			Equipo equipo = equipoDao.obtenerEquipoPorId(equipoId);

			return equipo != null && PRETEMPORADA.equals(equipo.getTipoTemporada());
		} catch (EmptyResultDataAccessException e) {
			return false;
		}
	}
}
