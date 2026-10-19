package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.ClasificacionFilaPublicDTO;
import com.mikedev.mutxamelcf.model.ClasificacionPublicDTO;
import com.mikedev.mutxamelcf.service.ClasificacionService;

@Service
public class ClasificacionServiceImpl implements ClasificacionService {

	private static final Logger logger = LoggerFactory.getLogger(ClasificacionServiceImpl.class);

	private final ClasificacionDao clasificacionDao;

	public ClasificacionServiceImpl(ClasificacionDao clasificacionDao) {
		this.clasificacionDao = clasificacionDao;
	}

	@Override
	@Transactional(readOnly = true)
	public ClasificacionPublicDTO obtenerPublicaPorEquipo(Long equipoId) {
		logger.debug("Inicio obtenerPublicaPorEquipo: equipoId={}", equipoId);

		List<Clasificacion> filas = clasificacionDao.obtenerPorEquipo(equipoId);

		if (filas.isEmpty()) {
			logger.debug("Fin obtenerPublicaPorEquipo: sin datos");
			return null;
		}

		// La competición, la jornada y la fecha son las de la fila del club;
		// si faltara, las de la primera fila.
		Clasificacion referencia = filas.stream().filter(Clasificacion::isEsNuestro).findFirst().orElse(filas.get(0));

		List<ClasificacionFilaPublicDTO> tabla = filas.stream()
				.map(f -> new ClasificacionFilaPublicDTO(f.getPosicion(), f.getNombreEquipo(), f.isEsNuestro(),
						f.getPuntos(), f.getPj(), f.getPg(), f.getPe(), f.getPp(), f.getGf(), f.getGc()))
				.toList();

		logger.debug("Fin obtenerPublicaPorEquipo: filas={}", tabla.size());

		return new ClasificacionPublicDTO(
				referencia.getCompeticion(), referencia.getJornada(), referencia.getFechaImportacion(), tabla);
	}

}
