package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.ClubRivalDao;
import com.mikedev.mutxamelcf.model.ClubRival;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.ClasificacionCompeticionDTO;
import com.mikedev.mutxamelcf.model.ClasificacionFilaPublicDTO;
import com.mikedev.mutxamelcf.model.ClasificacionPublicDTO;
import com.mikedev.mutxamelcf.service.ClasificacionService;

@Service
public class ClasificacionServiceImpl implements ClasificacionService {

	private static final Logger logger = LoggerFactory.getLogger(ClasificacionServiceImpl.class);

	private final ClasificacionDao clasificacionDao;
	private final ClubRivalDao clubRivalDao;
	private final int codigoClubPropio;

	public ClasificacionServiceImpl(
			ClasificacionDao clasificacionDao,
			ClubRivalDao clubRivalDao,
			@Value("${ffcv.club.codigo:3010}") int codigoClubPropio) {
		this.clasificacionDao = clasificacionDao;
		this.clubRivalDao = clubRivalDao;
		this.codigoClubPropio = codigoClubPropio;
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

		// Una tabla por competición (los equipos de la FFCV tienen una; Veteranos, la Liga y la Copa). Las filas ya
		// vienen por posición: dentro de cada competición se conserva ese orden.
		java.util.Map<String, List<Clasificacion>> porCompeticion = new java.util.LinkedHashMap<>();

		for (Clasificacion fila : filas) {
			porCompeticion.computeIfAbsent(String.valueOf(fila.getCompeticion()), k -> new java.util.ArrayList<>())
					.add(fila);
		}

		EscudosPorAlias porAlias = new EscudosPorAlias();

		List<ClasificacionCompeticionDTO> competiciones = new java.util.ArrayList<>();

		for (List<Clasificacion> grupo : porCompeticion.values()) {

			// La competición, la jornada y la fecha son las de la fila del club; si faltara, las de la primera fila.
			Clasificacion referencia = grupo.stream().filter(Clasificacion::isEsNuestro).findFirst()
					.orElse(grupo.get(0));

			List<ClasificacionFilaPublicDTO> tabla = grupo.stream()
					.map(f -> new ClasificacionFilaPublicDTO(f.getPosicion(), f.getNombreEquipo(), f.isEsNuestro(),
							f.getPuntos(), f.getPj(), f.getPg(), f.getPe(), f.getPp(), f.getGf(), f.getGc(),
							escudoUrl(f, porAlias),
							EscudosRivales.esPropioFila(f, codigoClubPropio)))
					.toList();

			competiciones.add(new ClasificacionCompeticionDTO(referencia.getCompeticion(), referencia.getJornada(),
					referencia.getFechaImportacion(), tabla));
		}

		// La principal: la de más equipos (si empatan, la primera por nombre), y va la primera; el resto, por nombre.
		competiciones.sort(java.util.Comparator
				.comparingInt((ClasificacionCompeticionDTO c) -> -c.clasificacion().size())
				.thenComparing(c -> String.valueOf(c.competicion()), String.CASE_INSENSITIVE_ORDER));

		ClasificacionCompeticionDTO principal = competiciones.get(0);

		logger.debug("Fin obtenerPublicaPorEquipo: competiciones={}, filas={}", competiciones.size(),
				principal.clasificacion().size());

		return new ClasificacionPublicDTO(principal.competicion(), principal.jornada(), principal.fechaImportacion(),
				principal.clasificacion(), List.copyOf(competiciones));
	}


	/*
	 * La URL del escudo de la fila: por el club que ya trae la consulta (código de club) y, en las filas antiguas sin
	 * código, por el alias de su nombre (una sola consulta de clubes, solo si hace falta). Nunca en nuestra fila ni en
	 * las de nuestro club: la app pone nuestro escudo con esNuestro.
	 */
	private String escudoUrl(Clasificacion fila, EscudosPorAlias porAlias) {

		if (fila.isEsNuestro() || (fila.getCodigoClub() != null && fila.getCodigoClub() == codigoClubPropio)
				|| NombreClub.esPropio(fila.getNombreEquipo())) {
			return null;
		}

		if (fila.getCodigoClub() != null) {
			return EscudosRivales.url(fila.getClubRivalId(), fila.getEscudoVersion());
		}

		return porAlias.url(NombreClub.normalizar(fila.getNombreEquipo()));
	}

	/** Alias → URL del escudo, cargado la primera vez que una fila sin código lo necesita. */
	private final class EscudosPorAlias {

		private java.util.Map<String, String> urls;

		String url(String alias) {

			if (urls == null) {
				urls = new java.util.HashMap<>();

				java.util.Map<Long, ClubRival> clubes = new java.util.HashMap<>();
				for (ClubRival club : clubRivalDao.listar()) {
					clubes.put(club.getId(), club);
				}

				clubRivalDao.mapaAlias().forEach((nombre, clubId) -> {
					ClubRival club = clubes.get(clubId);

					if (club != null && club.isTieneEscudo()) {
						urls.put(nombre, EscudosRivales.url(club.getId(), club.getEscudoVersion()));
					}
				});
			}

			return urls.get(alias);
		}
	}
}
