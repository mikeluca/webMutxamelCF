package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.PurgaComunicacionesDao;
import com.mikedev.mutxamelcf.service.PurgaComunicacionesService;

@Service
public class PurgaComunicacionesServiceImpl implements PurgaComunicacionesService {

	private static final Logger logger = LoggerFactory.getLogger(PurgaComunicacionesServiceImpl.class);

	private final PurgaComunicacionesDao purgaDao;

	public PurgaComunicacionesServiceImpl(PurgaComunicacionesDao purgaDao) {
		this.purgaDao = purgaDao;
	}

	/** Todo o nada: si una tabla falla, no se queda a medias una comunicación sin sus destinatarios. */
	@Override
	@Transactional
	public Map<String, Integer> purgar() {
		Timestamp limite = purgaDao.inicioDeHaceDosTemporadas();

		if (limite == null) {
			logger.info("Purga de comunicaciones: no hay dos temporadas anteriores a la activa, no se borra nada");

			return Map.of();
		}

		Map<String, Integer> borradas = purgaDao.purgarAnterioresA(limite);

		borradas.forEach((tabla, filas) -> logger.info("Purga de comunicaciones anteriores a {}: {} fila(s) borradas de {}",
				limite.toLocalDateTime().toLocalDate(), filas, tabla));

		return borradas;
	}
}
