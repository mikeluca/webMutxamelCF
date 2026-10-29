package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo;

/** De momento no hace nada: ahí se enganchará la generación automática de cuotas. */
@Component
public class AlAsignarEquipoDefinitivoNoOp implements AlAsignarEquipoDefinitivo {

	private static final Logger logger = LoggerFactory.getLogger(AlAsignarEquipoDefinitivoNoOp.class);

	@Override
	public void alAsignar(AsignacionDefinitiva asignacion) {
		logger.debug("Equipo definitivo asignado (sin acción): {}", asignacion);
	}
}
