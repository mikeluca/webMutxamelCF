package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.ClubRivalDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.ClubRival;
import com.mikedev.mutxamelcf.model.ClubRivalFila;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.ResumenGeneracionClubes;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.ClubRivalService;

@Service
public class ClubRivalServiceImpl implements ClubRivalService {

	private static final Logger logger = LoggerFactory.getLogger(ClubRivalServiceImpl.class);

	private static final int MAXIMO_NOMBRE = 150;

	private final ClubRivalDao clubRivalDao;
	private final ClasificacionDao clasificacionDao;
	private final PartidoDao partidoDao;
	private final TemporadaDao temporadaDao;
	private final EquipoDao equipoDao;
	private final int codigoClubPropio;

	public ClubRivalServiceImpl(
			ClubRivalDao clubRivalDao,
			ClasificacionDao clasificacionDao,
			PartidoDao partidoDao,
			TemporadaDao temporadaDao,
			EquipoDao equipoDao,
			@Value("${ffcv.club.codigo:3010}") int codigoClubPropio) {

		this.clubRivalDao = clubRivalDao;
		this.clasificacionDao = clasificacionDao;
		this.partidoDao = partidoDao;
		this.temporadaDao = temporadaDao;
		this.equipoDao = equipoDao;
		this.codigoClubPropio = codigoClubPropio;
	}

	/** Contadores de "Generar clubes". */
	private static final class Contadores {
		int clubes;
		int alias;
		int partidos;
	}

	// ------------------------------------------------------------------
	// Creación automática
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public Long asegurarDeClasificacion(Integer codigoClub, String nombreEquipo, boolean esNuestro) {
		return asegurarDeClasificacion(codigoClub, nombreEquipo, esNuestro, new Contadores());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean necesitaClubNuevo(String nombreEquipo) {

		if (NombreClub.esPropio(nombreEquipo)) {
			return false;
		}

		String normalizado = NombreClub.normalizar(nombreEquipo);

		if (normalizado.isEmpty() || clubRivalDao.obtenerPorAlias(normalizado) != null) {
			return false;
		}

		ClubRival mismoNombre = clubRivalDao.obtenerPorNombre(NombreClub.nombreVisible(nombreEquipo));

		return mismoNombre == null || !NombreClub.normalizar(mismoNombre.getNombre()).equals(normalizado);
	}

	@Override
	@Transactional
	public Long asegurarPorNombre(String rival) {
		return asegurarDeClasificacion(null, rival, false, new Contadores());
	}

	private Long asegurarDeClasificacion(Integer codigoClub, String nombre, boolean esNuestro, Contadores c) {

		// Nuestro club nunca es un rival: ni por código, ni por marca de la fila, ni por nombre.
		if (esNuestro || (codigoClub != null && codigoClub == codigoClubPropio) || NombreClub.esPropio(nombre)) {
			return null;
		}

		String normalizado = NombreClub.normalizar(nombre);

		if (normalizado.isEmpty()) {
			return null;
		}

		ClubRival porAlias = clubRivalDao.obtenerPorAlias(normalizado);

		if (codigoClub != null) {

			ClubRival porCodigo = clubRivalDao.obtenerPorCodigo(codigoClub);

			if (porCodigo != null) {

				if (porAlias == null) {
					clubRivalDao.anadirAlias(porCodigo.getId(), normalizado);
					c.alias++;
				} else if (!porAlias.getId().equals(porCodigo.getId()) && porAlias.getCodigoFfcv() == null) {
					// Un club creado antes por nombre que resulta ser este: se unen en el que tiene el código.
					unirInterno(porAlias.getId(), porCodigo.getId());
					logger.info("Clubes unidos al importar con código: origen={}, destino={}", porAlias.getId(),
							porCodigo.getId());
				}

				return porCodigo.getId();
			}

			if (porAlias != null && porAlias.getCodigoFfcv() == null) {
				// El club creado por nombre recibe su código.
				clubRivalDao.ponerCodigo(porAlias.getId(), codigoClub);
				return porAlias.getId();
			}

			// No existe: se crea. Si el alias es de otro club con otro código (dos clubes distintos con el mismo
			// nombre), el nuevo se queda sin ese alias: el alias es de un solo club.
			Long id = crearClub(NombreClub.nombreVisible(nombre), codigoClub, c);

			if (porAlias == null && clubRivalDao.anadirAlias(id, normalizado)) {
				c.alias++;
			}

			return id;
		}

		if (porAlias != null) {
			return porAlias.getId();
		}

		// Sin código: un club ya creado con ese nombre exacto y que se normaliza igual recibe el alias.
		ClubRival mismoNombre = clubRivalDao.obtenerPorNombre(NombreClub.nombreVisible(nombre));

		if (mismoNombre != null && NombreClub.normalizar(mismoNombre.getNombre()).equals(normalizado)) {
			clubRivalDao.anadirAlias(mismoNombre.getId(), normalizado);
			c.alias++;
			return mismoNombre.getId();
		}

		Long id = crearClub(NombreClub.nombreVisible(nombre), null, c);

		if (clubRivalDao.anadirAlias(id, normalizado)) {
			c.alias++;
		}

		return id;
	}

	private Long crearClub(String nombreVisible, Integer codigo, Contadores c) {

		String nombre = nombreVisible.length() > MAXIMO_NOMBRE ? nombreVisible.substring(0, MAXIMO_NOMBRE)
				: nombreVisible;

		// El nombre es único: si ya existe en otro club, se distingue con el código (o con un número).
		if (clubRivalDao.obtenerPorNombre(nombre) != null) {

			String base = codigo != null ? nombre + " (" + codigo + ")" : nombre;
			nombre = base;

			for (int n = 2; clubRivalDao.obtenerPorNombre(nombre) != null; n++) {
				nombre = base + " (" + n + ")";
			}
		}

		c.clubes++;

		return clubRivalDao.crear(nombre, codigo);
	}

	@Override
	@Transactional
	public void enlazarPartido(Long partidoId, String rival) {

		if (partidoId == null) {
			return;
		}

		partidoDao.actualizarClubRival(partidoId, asegurarPorNombre(rival));
	}

	@Override
	@Transactional
	public ResumenGeneracionClubes generar() {

		Contadores c = new Contadores();

		Temporada activa = temporadaDao.obtenerTemporadaActiva();

		if (activa != null) {
			for (Clasificacion fila : clasificacionDao.listarPorTemporada(activa.getId())) {
				asegurarDeClasificacion(fila.getCodigoClub(), fila.getNombreEquipo(), fila.isEsNuestro(), c);
			}
		}

		// Los partidos de la temporada activa y de las anteriores (los de temporadas futuras no existen aún).
		LocalDate hasta = activa == null || activa.getFechaFin() == null ? null
				: new java.sql.Date(activa.getFechaFin().getTime()).toLocalDate();

		for (Partido partido : partidoDao.obtenerSinClubRival(hasta)) {

			Long clubId = asegurarDeClasificacion(null, partido.getRival(), false, c);

			if (clubId != null) {
				partidoDao.actualizarClubRival(partido.getId(), clubId);
				c.partidos++;
			}
		}

		logger.info("Clubes generados: creados={}, alias={}, partidos={}", c.clubes, c.alias, c.partidos);

		return new ResumenGeneracionClubes(c.clubes, c.alias, c.partidos);
	}

	// ------------------------------------------------------------------
	// Panel
	// ------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<ClubRivalFila> listar() {

		List<ClubRival> clubes = clubRivalDao.listar();
		Map<Long, List<String>> alias = clubRivalDao.aliasPorClub();
		Map<Long, Integer> partidos = clubRivalDao.contarPartidosPorClub();
		Map<Long, List<String>> equiposDePartidos = clubRivalDao.equiposPorClubDePartidos();

		Map<Long, Set<String>> ligas = new HashMap<>();
		Map<Long, Set<String>> equipos = new HashMap<>();

		equiposDePartidos.forEach((clubId, nombres) -> equipos.computeIfAbsent(clubId, k -> new LinkedHashSet<>())
				.addAll(nombres));

		Temporada activa = temporadaDao.obtenerTemporadaActiva();

		if (activa != null) {

			Map<Integer, Long> porCodigo = new HashMap<>();
			Map<String, Long> porAlias = clubRivalDao.mapaAlias();

			for (ClubRival club : clubes) {
				if (club.getCodigoFfcv() != null) {
					porCodigo.put(club.getCodigoFfcv(), club.getId());
				}
			}

			Map<Long, String> nombresEquipo = new HashMap<>();

			for (Equipo equipo : equipoDao.obtenerActivos()) {
				nombresEquipo.put(equipo.getId(), equipo.getNombre());
			}

			for (Clasificacion fila : clasificacionDao.listarPorTemporada(activa.getId())) {

				if (fila.isEsNuestro()) {
					continue;
				}

				Long clubId = fila.getCodigoClub() != null ? porCodigo.get(fila.getCodigoClub()) : null;

				if (clubId == null) {
					clubId = porAlias.get(NombreClub.normalizar(fila.getNombreEquipo()));
				}

				if (clubId == null) {
					continue;
				}

				if (fila.getCompeticion() != null) {
					ligas.computeIfAbsent(clubId, k -> new LinkedHashSet<>()).add(fila.getCompeticion());
				}

				String equipo = nombresEquipo.get(fila.getEquipoId());

				if (equipo != null) {
					equipos.computeIfAbsent(clubId, k -> new LinkedHashSet<>()).add(equipo);
				}
			}
		}

		List<ClubRivalFila> filas = new ArrayList<>(clubes.size());

		for (ClubRival club : clubes) {
			filas.add(new ClubRivalFila(
					club.getId(),
					club.getNombre(),
					club.getCodigoFfcv(),
					club.isTieneEscudo(),
					club.isTieneEscudo() ? EscudosRivales.url(club.getId(), club.getEscudoVersion()) : null,
					new ArrayList<>(ligas.getOrDefault(club.getId(), Set.of())),
					new ArrayList<>(equipos.getOrDefault(club.getId(), Set.of())),
					partidos.getOrDefault(club.getId(), 0),
					alias.getOrDefault(club.getId(), List.of())));
		}

		return filas;
	}

	@Override
	@Transactional(readOnly = true)
	public int[] contarConEscudo() {

		List<ClubRival> clubes = clubRivalDao.listar();
		int con = 0;

		for (ClubRival club : clubes) {
			if (club.isTieneEscudo()) {
				con++;
			}
		}

		return new int[] { con, clubes.size() };
	}

	@Override
	@Transactional(readOnly = true)
	public ClubRival obtener(Long id) {
		return id == null ? null : clubRivalDao.obtenerPorId(id);
	}

	@Override
	@Transactional
	public void subirEscudo(Long id, byte[] archivo) {

		existente(id);

		// Primero se valida y se reduce: un archivo malo no toca nada.
		byte[] png = EscudoImagen.procesar(archivo);

		clubRivalDao.guardarEscudo(id, png);

		logger.info("Escudo de club rival guardado: clubId={}, bytes={}", id, png.length);
	}

	@Override
	@Transactional
	public void quitarEscudo(Long id) {

		ClubRival club = existente(id);

		if (!club.isTieneEscudo()) {
			return;
		}

		clubRivalDao.quitarEscudo(id);

		logger.info("Escudo de club rival quitado: clubId={}", id);
	}

	@Override
	@Transactional
	public void renombrar(Long id, String nombre) {

		existente(id);

		String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");

		if (limpio.isEmpty()) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}

		if (limpio.length() > MAXIMO_NOMBRE) {
			throw new IllegalArgumentException("El nombre no puede pasar de " + MAXIMO_NOMBRE + " caracteres");
		}

		ClubRival otro = clubRivalDao.obtenerPorNombre(limpio);

		if (otro != null && !otro.getId().equals(id)) {
			throw new IllegalArgumentException("Ya existe un club con ese nombre");
		}

		clubRivalDao.renombrar(id, limpio);
	}

	@Override
	@Transactional
	public void unir(Long origenId, Long destinoId) {

		if (origenId == null || destinoId == null || origenId.equals(destinoId)) {
			throw new IllegalArgumentException("Elige dos clubes distintos para unirlos");
		}

		unirInterno(origenId, destinoId);
	}

	private void unirInterno(Long origenId, Long destinoId) {

		ClubRival origen = existente(origenId);
		ClubRival destino = existente(destinoId);

		if (origen.getCodigoFfcv() != null && destino.getCodigoFfcv() != null
				&& !origen.getCodigoFfcv().equals(destino.getCodigoFfcv())) {
			throw new IllegalArgumentException("No se pueden unir dos clubes con códigos de la FFCV distintos ("
					+ origen.getCodigoFfcv() + " y " + destino.getCodigoFfcv() + ")");
		}

		// El código pasa al destino si este no lo tenía (es único: primero se libera en el origen).
		if (origen.getCodigoFfcv() != null && destino.getCodigoFfcv() == null) {
			clubRivalDao.ponerCodigo(origenId, null);
			clubRivalDao.ponerCodigo(destinoId, origen.getCodigoFfcv());
		}

		// Si los dos tienen escudo se conserva el del destino; si solo lo tiene el origen, pasa al destino.
		if (origen.isTieneEscudo() && !destino.isTieneEscudo()) {
			clubRivalDao.copiarEscudo(origenId, destinoId);
		}

		clubRivalDao.moverAlias(origenId, destinoId);
		clubRivalDao.moverPartidos(origenId, destinoId);
		clubRivalDao.borrar(origenId);

		logger.info("Clubes rivales unidos: origen={}, destino={}", origenId, destinoId);
	}

	@Override
	@Transactional
	public void borrar(Long id) {

		ClubRival club = existente(id);

		if (clubRivalDao.contarPartidos(id) > 0 || clubRivalDao.contarClasificaciones(id) > 0
				|| apareceEnClasificacionesPorNombre(club)) {
			throw new IllegalArgumentException(
					"No se puede borrar: el club tiene partidos o aparece en alguna clasificación");
		}

		clubRivalDao.borrar(id);

		logger.info("Club rival borrado: clubId={}", id);
	}

	// Las filas antiguas sin código se reconocen por el nombre.
	private boolean apareceEnClasificacionesPorNombre(ClubRival club) {

		Temporada activa = temporadaDao.obtenerTemporadaActiva();

		if (activa == null) {
			return false;
		}

		Set<String> alias = new LinkedHashSet<>(clubRivalDao.listarAlias(club.getId()));

		for (Clasificacion fila : clasificacionDao.listarPorTemporada(activa.getId())) {
			if (fila.getCodigoClub() == null && !fila.isEsNuestro()
					&& alias.contains(NombreClub.normalizar(fila.getNombreEquipo()))) {
				return true;
			}
		}

		return false;
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] obtenerEscudo(Long id) {
		return id == null ? null : clubRivalDao.obtenerEscudo(id);
	}

	private ClubRival existente(Long id) {

		ClubRival club = id == null ? null : clubRivalDao.obtenerPorId(id);

		if (club == null) {
			throw new IllegalArgumentException("El club no existe");
		}

		return club;
	}
}
