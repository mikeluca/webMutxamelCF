package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.AlQuitarEquipoDefinitivo;

/** De momento no hace nada: ahí se enganchará la anulación de las cuotas generadas al asignar. */
@Component
public class AlQuitarEquipoDefinitivoNoOp implements AlQuitarEquipoDefinitivo {

	private static final Logger logger = LoggerFactory.getLogger(AlQuitarEquipoDefinitivoNoOp.class);

	@Override
	public void alQuitar(QuitaDefinitiva quita) {
		logger.debug("Equipo definitivo quitado (sin acción): {}", quita);
	}
}
