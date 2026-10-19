package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.ClasificacionSinMapeo;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoClasificacionPrevia;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.FfcvClasificacionesParseResult;
import com.mikedev.mutxamelcf.model.FfcvFilaClasificacion;
import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;
import com.mikedev.mutxamelcf.service.ImportacionClasificacionesService;

@Service
public class ImportacionClasificacionesServiceImpl implements ImportacionClasificacionesService {

	private static final Logger logger = LoggerFactory.getLogger(ImportacionClasificacionesServiceImpl.class);

	private static final int MAXIMO_ERRORES = 100;

	private final ClasificacionDao clasificacionDao;
	private final EquipoFfcvDao equipoFfcvDao;
	private final EquipoDao equipoDao;
	private final int codigoClub;

	/**
	 * @param codigoClub código de club del Mutxamel C.F. en la FFCV. El club se
	 *                   identifica siempre por él, nunca por el texto del nombre
	 *                   (Monnegre y Benifutsal también contienen "Mutxamel").
	 */
	public ImportacionClasificacionesServiceImpl(
			ClasificacionDao clasificacionDao,
			EquipoFfcvDao equipoFfcvDao,
			EquipoDao equipoDao,
			@Value("${ffcv.club.codigo:3010}") int codigoClub) {

		this.clasificacionDao = clasificacionDao;
		this.equipoFfcvDao = equipoFfcvDao;
		this.equipoDao = equipoDao;
		this.codigoClub = codigoClub;
	}

	@Override
	@Transactional(readOnly = true)
	public PrevisualizacionClasificaciones previsualizar(byte[] archivo) {
		logger.debug("Inicio previsualizar: bytes={}", archivo != null ? archivo.length : 0);

		FfcvClasificacionesParseResult parseado = FfcvClasificacionesParser.parsear(archivo);

		if (parseado.filas().isEmpty() && parseado.errores().isEmpty()) {
			throw new IllegalArgumentException("El archivo no contiene ninguna clasificación");
		}

		Map<String, List<FfcvFilaClasificacion>> grupos = new LinkedHashMap<>();
		for (FfcvFilaClasificacion fila : parseado.filas()) {
			grupos.computeIfAbsent(fila.competicion(), k -> new ArrayList<>()).add(fila);
		}

		Map<Long, Equipo> equipos = new HashMap<>();
		for (Equipo equipo : equipoDao.obtenerTodos()) {
			equipos.put(equipo.getId(), equipo);
		}

		List<EquipoFfcv> relaciones = equipoFfcvDao.listarTodos();

		List<EquipoClasificacionPrevia> previas = new ArrayList<>();
		Set<String> clavesMapeadas = new HashSet<>();

		for (EquipoFfcv relacion : relaciones) {

			clavesMapeadas.add(clave(relacion.getCompeticion(), relacion.getLetra()));

			Equipo equipo = equipos.get(relacion.getEquipoId());

			if (equipo == null) {
				continue;
			}

			previas.add(resolver(equipo, relacion, grupos, parseado.gruposConErrores()));
		}

		previas.sort(Comparator.comparing(EquipoClasificacionPrevia::equipoNombre,
				Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

		PrevisualizacionClasificaciones resultado = new PrevisualizacionClasificaciones(
				parseado.filas().size(),
				grupos.size(),
				previas,
				filasSinMapeo(parseado.filas(), clavesMapeadas),
				parseado.errores().size() > MAXIMO_ERRORES
						? parseado.errores().subList(0, MAXIMO_ERRORES)
						: parseado.errores());

		logger.debug("Fin previsualizar: filas={}, grupos={}, equipos={}, sinMapeo={}",
				resultado.filasLeidas(), resultado.competiciones(), previas.size(), resultado.sinMapeo().size());

		return resultado;
	}

	@Override
	@Transactional
	public ResultadoImportacionClasificaciones confirmar(
			String usuario,
			PrevisualizacionClasificaciones previsualizacion,
			Collection<Long> equiposMarcados) {

		logger.debug("Inicio confirmar");

		if (previsualizacion == null || previsualizacion.equipos() == null) {
			throw new IllegalArgumentException("No hay ninguna vista previa que confirmar");
		}

		if (equiposMarcados == null || equiposMarcados.isEmpty()) {
			throw new IllegalArgumentException("No hay ningún equipo marcado");
		}

		Set<Long> idsDeLaPrevia = new HashSet<>();
		for (EquipoClasificacionPrevia previa : previsualizacion.equipos()) {
			idsDeLaPrevia.add(previa.equipoId());
		}

		for (Long id : equiposMarcados) {
			if (id == null || !idsDeLaPrevia.contains(id)) {
				throw new IllegalArgumentException("Hay un equipo que no pertenece a la vista previa");
			}
		}

		LocalDateTime ahora = LocalDateTime.now();
		List<String> importados = new ArrayList<>();
		List<String> avisos = new ArrayList<>();
		int filasInsertadas = 0;

		for (EquipoClasificacionPrevia previa : previsualizacion.equipos()) {

			if (!equiposMarcados.contains(previa.equipoId())) {
				continue;
			}

			if (!previa.importable() || previa.tabla() == null || previa.tabla().isEmpty()) {
				avisos.add(previa.equipoNombre() + ": no se ha importado (" + previa.motivo() + ").");
				continue;
			}

			if (equipoDao.obtenerEquipoPorId(previa.equipoId()) == null) {
				avisos.add(previa.equipoNombre() + ": el equipo ya no existe, no se ha importado.");
				continue;
			}

			List<Clasificacion> nuevas = new ArrayList<>(previa.tabla().size());
			for (Clasificacion fila : previa.tabla()) {
				nuevas.add(copiar(fila, previa.equipoId(), ahora));
			}

			clasificacionDao.eliminarPorEquipo(previa.equipoId());
			clasificacionDao.insertarTodas(nuevas);

			importados.add(previa.equipoNombre());
			filasInsertadas += nuevas.size();
		}

		// Privacidad: solo el usuario del panel y contadores.
		logger.info("Importación de clasificaciones confirmada: usuario={}, equipos={}, filas={}, avisos={}",
				usuario, importados.size(), filasInsertadas, avisos.size());

		logger.debug("Fin confirmar");

		return new ResultadoImportacionClasificaciones(importados.size(), filasInsertadas, importados, avisos);
	}

	/*
	 * Qué hay en el archivo para este equipo. No escribe nada.
	 */
	private EquipoClasificacionPrevia resolver(
			Equipo equipo,
			EquipoFfcv relacion,
			Map<String, List<FfcvFilaClasificacion>> grupos,
			Set<String> gruposConErrores) {

		String competicion = relacion.getCompeticion();
		String letra = normalizarLetra(relacion.getLetra());

		if (gruposConErrores.contains(competicion)) {
			return noEncontrado(equipo, competicion,
					"el archivo tiene filas ilegibles en este grupo y la tabla estaría incompleta");
		}

		List<FfcvFilaClasificacion> grupo = grupos.get(competicion);

		if (grupo == null) {
			return noEncontrado(equipo, competicion, "la competición no aparece en el archivo");
		}

		FfcvFilaClasificacion nuestra = grupo.stream()
				.filter(f -> f.club() != null && f.club() == codigoClub && Objects.equals(f.letra(), letra))
				.findFirst()
				.orElse(null);

		if (nuestra == null) {
			return noEncontrado(equipo, competicion, "el grupo está en el archivo pero no aparece el club "
					+ codigoClub + (letra != null ? " con la letra " + letra : " sin letra"));
		}

		List<Clasificacion> tabla = grupo.stream()
				.sorted(Comparator.comparingInt(FfcvFilaClasificacion::posicion)
						.thenComparing(FfcvFilaClasificacion::equipo, String.CASE_INSENSITIVE_ORDER))
				.map(f -> aClasificacion(f, equipo.getId(), f == nuestra))
				.toList();

		List<Clasificacion> guardadas = clasificacionDao.obtenerPorEquipo(equipo.getId());
		Clasificacion guardadaNuestra = guardadas.stream().filter(Clasificacion::isEsNuestro).findFirst().orElse(null);

		Integer jornadaGuardada = guardadaNuestra != null ? guardadaNuestra.getJornada() : null;

		boolean antiguo = jornadaGuardada != null && nuestra.jornada() != null
				&& nuestra.jornada() < jornadaGuardada;

		List<String> cambios = cambios(guardadaNuestra, nuestra, competicion, guardadas, tabla);

		return new EquipoClasificacionPrevia(
				equipo.getId(), equipo.getNombre(), true, null, competicion,
				nuestra.jornada(), jornadaGuardada, nuestra.posicion(), nuestra.puntos(), nuestra.pj(),
				antiguo, true, !antiguo, !cambios.isEmpty(), cambios, tabla);
	}

	private static EquipoClasificacionPrevia noEncontrado(Equipo equipo, String competicion, String motivo) {
		return new EquipoClasificacionPrevia(
				equipo.getId(), equipo.getNombre(), false, motivo, competicion,
				null, null, null, null, null, false, false, false, false, List.of(), List.of());
	}

	private static List<String> cambios(
			Clasificacion guardadaNuestra,
			FfcvFilaClasificacion nuestra,
			String competicion,
			List<Clasificacion> guardadas,
			List<Clasificacion> tabla) {

		if (guardadas.isEmpty()) {
			return List.of("Primera importación");
		}

		List<String> cambios = new ArrayList<>();

		if (guardadaNuestra == null) {
			cambios.add("La clasificación guardada no tenía la fila del club");
		} else {
			anadirCambio(cambios, "Competición", guardadaNuestra.getCompeticion(), competicion);
			anadirCambio(cambios, "Jornada", guardadaNuestra.getJornada(), nuestra.jornada());
			anadirCambio(cambios, "Posición", guardadaNuestra.getPosicion(), nuestra.posicion());
			anadirCambio(cambios, "Puntos", guardadaNuestra.getPuntos(), nuestra.puntos());
			anadirCambio(cambios, "PJ", guardadaNuestra.getPj(), nuestra.pj());
		}

		// Los rivales también juegan: la tabla puede cambiar aunque nuestra fila no.
		if (cambios.isEmpty() && !mismaTabla(guardadas, tabla)) {
			cambios.add("Cambian otras filas de la tabla");
		}

		return cambios;
	}

	private static void anadirCambio(List<String> cambios, String campo, Object antes, Object despues) {
		if (!Objects.equals(antes, despues)) {
			cambios.add(campo + ": " + (antes == null ? "—" : antes) + " → " + (despues == null ? "—" : despues));
		}
	}

	private static boolean mismaTabla(List<Clasificacion> guardadas, List<Clasificacion> nueva) {

		if (guardadas.size() != nueva.size()) {
			return false;
		}

		// Se comparan ordenadas por la propia clave: el orden de los empates
		// en posición no es el mismo en la base de datos que en el archivo.
		return claves(guardadas).equals(claves(nueva));
	}

	private static List<String> claves(List<Clasificacion> filas) {
		return filas.stream()
				.map(c -> String.join("|", String.valueOf(c.getPosicion()), c.getNombreEquipo(),
						String.valueOf(c.getLetra()), String.valueOf(c.isEsNuestro()), String.valueOf(c.getJornada()),
						String.valueOf(c.getPuntos()), String.valueOf(c.getPj()), String.valueOf(c.getPg()),
						String.valueOf(c.getPe()), String.valueOf(c.getPp()), String.valueOf(c.getGf()),
						String.valueOf(c.getGc())))
				.sorted()
				.toList();
	}

	/*
	 * Filas del club (por su código) en grupos sin relación en EQUIPOS_FFCV: un
	 * equipo nuevo al que todavía no se ha asignado grupo.
	 */
	private List<ClasificacionSinMapeo> filasSinMapeo(List<FfcvFilaClasificacion> filas, Set<String> clavesMapeadas) {

		Set<String> vistas = new LinkedHashSet<>();
		List<ClasificacionSinMapeo> sinMapeo = new ArrayList<>();

		for (FfcvFilaClasificacion fila : filas) {

			if (fila.club() == null || fila.club() != codigoClub) {
				continue;
			}

			String clave = clave(fila.competicion(), fila.letra());

			if (!clavesMapeadas.contains(clave) && vistas.add(clave)) {
				sinMapeo.add(new ClasificacionSinMapeo(fila.competicion(), fila.letra(), fila.equipo()));
			}
		}

		sinMapeo.sort(Comparator.comparing(ClasificacionSinMapeo::competicion));

		return sinMapeo;
	}

	private static Clasificacion aClasificacion(FfcvFilaClasificacion fila, Long equipoId, boolean esNuestro) {

		Clasificacion c = new Clasificacion();
		c.setEquipoId(equipoId);
		c.setCompeticion(fila.competicion());
		c.setJornada(fila.jornada());
		c.setPosicion(fila.posicion());
		c.setNombreEquipo(fila.equipo());
		c.setLetra(fila.letra());
		c.setEsNuestro(esNuestro);
		c.setPuntos(fila.puntos());
		c.setPj(fila.pj());
		c.setPg(fila.pg());
		c.setPe(fila.pe());
		c.setPp(fila.pp());
		c.setGf(fila.gf());
		c.setGc(fila.gc());
		c.setFechaUltPartido(fila.fechaUltPartido());
		return c;
	}

	private static Clasificacion copiar(Clasificacion origen, Long equipoId, LocalDateTime fechaImportacion) {

		Clasificacion c = new Clasificacion();
		c.setEquipoId(equipoId);
		c.setCompeticion(origen.getCompeticion());
		c.setJornada(origen.getJornada());
		c.setPosicion(origen.getPosicion());
		c.setNombreEquipo(origen.getNombreEquipo());
		c.setLetra(origen.getLetra());
		c.setEsNuestro(origen.isEsNuestro());
		c.setPuntos(origen.getPuntos());
		c.setPj(origen.getPj());
		c.setPg(origen.getPg());
		c.setPe(origen.getPe());
		c.setPp(origen.getPp());
		c.setGf(origen.getGf());
		c.setGc(origen.getGc());
		c.setFechaUltPartido(origen.getFechaUltPartido());
		c.setFechaImportacion(fechaImportacion);
		return c;
	}

	private static String normalizarLetra(String letra) {
		return letra == null || letra.isBlank() ? null : letra.trim().toUpperCase();
	}

	private static String clave(String competicion, String letra) {
		return competicion + "|" + (letra == null ? "" : letra);
	}

}
