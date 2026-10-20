package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.DescansoEquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.AsignacionEquipoFfcv;
import com.mikedev.mutxamelcf.model.DescansoEquipo;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.EstadoFilaFfcv;
import com.mikedev.mutxamelcf.model.FfcvDesaparecido;
import com.mikedev.mutxamelcf.model.FfcvDescanso;
import com.mikedev.mutxamelcf.model.FfcvErrorLinea;
import com.mikedev.mutxamelcf.model.FfcvFilaCsv;
import com.mikedev.mutxamelcf.model.FfcvFilaPrevisualizacion;
import com.mikedev.mutxamelcf.model.FfcvParseResult;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;
import com.mikedev.mutxamelcf.service.ImportacionFfcvService;

@Service
public class ImportacionFfcvServiceImpl implements ImportacionFfcvService {

	private static final Logger logger = LoggerFactory.getLogger(ImportacionFfcvServiceImpl.class);

	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final String TIPO_LIGA = "LIGA";
	private static final int MAXIMO_FILAS = 500;
	private static final int MAXIMO_ERRORES = 100;
	private static final int LONGITUD_MAXIMA_TEXTO = 150;

	private final PartidoDao partidoDao;
	private final EquipoFfcvDao equipoFfcvDao;
	private final DescansoEquipoDao descansoEquipoDao;
	private final EquipoDao equipoDao;
	private final ConvocatoriaDao convocatoriaDao;

	public ImportacionFfcvServiceImpl(
			PartidoDao partidoDao,
			EquipoFfcvDao equipoFfcvDao,
			DescansoEquipoDao descansoEquipoDao,
			EquipoDao equipoDao,
			ConvocatoriaDao convocatoriaDao) {

		this.partidoDao = partidoDao;
		this.equipoFfcvDao = equipoFfcvDao;
		this.descansoEquipoDao = descansoEquipoDao;
		this.equipoDao = equipoDao;
		this.convocatoriaDao = convocatoriaDao;
	}

	@Override
	@Transactional(readOnly = true)
	public PrevisualizacionFfcv previsualizar(byte[] archivo) {
		logger.debug("Inicio previsualizar: bytes={}", archivo != null ? archivo.length : 0);

		if (archivo == null || archivo.length == 0) {
			throw new IllegalArgumentException("El archivo está vacío");
		}

		FfcvParseResult parseado = FfcvCsvParser.parsear(archivo);

		if (parseado.filas().isEmpty() && parseado.errores().isEmpty()) {
			throw new IllegalArgumentException("El archivo no contiene ningún partido del Mutxamel");
		}

		PrevisualizacionFfcv resultado = resolver(parseado.filas(), parseado.errores());

		logger.debug("Fin previsualizar: filas={}, errores={}", resultado.filas().size(), resultado.errores().size());

		return resultado;
	}

	@Override
	@Transactional
	public ResultadoImportacionFfcv confirmar(
			String usuario,
			PrevisualizacionFfcv previsualizacion,
			List<AsignacionEquipoFfcv> asignaciones) {

		logger.debug("Inicio confirmar");

		if (previsualizacion == null || previsualizacion.filas() == null || previsualizacion.filas().isEmpty()) {
			throw new IllegalArgumentException("No hay partidos que importar");
		}

		if (previsualizacion.filas().size() > MAXIMO_FILAS) {
			throw new IllegalArgumentException("Demasiadas filas para importar de una vez");
		}

		List<FfcvFilaCsv> filas = new ArrayList<>();

		for (FfcvFilaPrevisualizacion fila : previsualizacion.filas()) {
			filas.add(normalizarFila(fila.aFilaCsv()));
		}

		guardarAsignaciones(asignaciones, filas);

		// Se recalcula todo contra la base de datos (ya con las relaciones nuevas).
		PrevisualizacionFfcv resuelta = resolver(filas, List.of());

		Map<EstadoFilaFfcv, Integer> contadores = new EnumMap<>(EstadoFilaFfcv.class);
		Set<Long> equiposConPartido = new HashSet<>();
		List<Long> procesados = new ArrayList<>();

		for (FfcvFilaPrevisualizacion fila : resuelta.filas()) {

			contadores.merge(fila.estado(), 1, Integer::sum);

			if (fila.equipoId() != null) {
				equiposConPartido.add(fila.equipoId());
			}

			if (fila.estado() == EstadoFilaFfcv.NUEVO) {
				procesados.add(partidoDao.crear(construirPartido(fila)).getId());
			} else if (fila.estado() == EstadoFilaFfcv.ACTUALIZADO) {
				partidoDao.actualizarDatosFfcv(construirPartido(fila));
				procesados.add(fila.partidoId());
			} else if (fila.estado() == EstadoFilaFfcv.SIN_CAMBIOS && fila.partidoId() != null) {
				// También los que no cambian: la importación los ha comprobado.
				procesados.add(fila.partidoId());
			}
		}

		// FECHA_IMPORTACION (no FECHA_ACTUALIZACION, que es solo edición manual).
		partidoDao.marcarFechaImportacion(procesados);

		int[] descansos = sincronizarDescansos(resuelta, equiposConPartido);

		List<String> avisos = construirAvisos(resuelta);

		ResultadoImportacionFfcv resultado = new ResultadoImportacionFfcv(
				contadores.getOrDefault(EstadoFilaFfcv.NUEVO, 0),
				contadores.getOrDefault(EstadoFilaFfcv.ACTUALIZADO, 0),
				contadores.getOrDefault(EstadoFilaFfcv.SIN_CAMBIOS, 0),
				contadores.getOrDefault(EstadoFilaFfcv.CONFLICTO_MANUAL, 0),
				contadores.getOrDefault(EstadoFilaFfcv.SIN_EQUIPO, 0),
				descansos[0],
				descansos[1],
				avisos);

		// Privacidad: solo el usuario del panel, el fin de semana y contadores.
		logger.info(
				"Importación FFCV confirmada: usuario={}, finDeSemana={}..{}, nuevos={}, actualizados={}, "
						+ "sinCambios={}, sinEquipo={}, conflictosManuales={}, descansosCreados={}, "
						+ "descansosEliminados={}, desaparecidos={}",
				usuario, resuelta.desde(), resuelta.hasta(),
				resultado.creados(), resultado.actualizados(), resultado.sinCambios(), resultado.sinEquipo(),
				resultado.conflictosManuales(), resultado.descansosCreados(), resultado.descansosEliminados(),
				resuelta.desaparecidos().size());

		logger.debug("Fin confirmar");

		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hayImportacionParaFinDeSemana(LocalDate sabado) {

		// Viernes a lunes: los CSV de fútbol sala o aplazados pueden empezar el viernes.
		LocalDate desde = sabado.minusDays(1);
		LocalDate hasta = sabado.plusDays(2);

		boolean hayPartidosFfcv = partidoDao.obtenerPorRangoFechas(desde, hasta).stream()
				.anyMatch(p -> Partido.ORIGEN_FFCV.equals(p.getOrigen()));

		return hayPartidosFfcv || !descansoEquipoDao.obtenerPorRangoFechas(desde, hasta).isEmpty();
	}

	/*
	 * Relaciones nuevas elegidas en la vista previa. Solo se aceptan para
	 * una competición+letra que realmente venga en el archivo y que todavía
	 * no esté relacionada; el equipo debe existir.
	 */
	private void guardarAsignaciones(List<AsignacionEquipoFfcv> asignaciones, List<FfcvFilaCsv> filas) {

		if (asignaciones == null) {
			return;
		}

		Set<String> clavesDelArchivo = new HashSet<>();
		for (FfcvFilaCsv fila : filas) {
			clavesDelArchivo.add(clave(fila.competicion(), fila.letra()));
		}

		Set<String> yaProcesadas = new HashSet<>();

		for (AsignacionEquipoFfcv asignacion : asignaciones) {

			if (asignacion == null || asignacion.equipoId() == null) {
				continue;
			}

			String competicion = FfcvCsvParser.normalizarCompeticion(asignacion.competicion());
			String letra = normalizarLetra(asignacion.letra());
			String clave = clave(competicion, letra);

			if (!clavesDelArchivo.contains(clave)) {
				throw new IllegalArgumentException("La relación indicada no corresponde a ningún partido del archivo");
			}

			if (!yaProcesadas.add(clave)) {
				continue;
			}

			if (equipoDao.obtenerEquipoPorId(asignacion.equipoId()) == null) {
				throw new IllegalArgumentException("El equipo indicado no existe");
			}

			EquipoFfcv existente = equipoFfcvDao.obtenerPorCompeticionYLetra(competicion, letra);

			if (existente != null) {
				// Ya relacionada (p.ej. por otro usuario a la vez): no se pisa.
				continue;
			}

			equipoFfcvDao.crear(new EquipoFfcv(null, asignacion.equipoId(), competicion, letra));
		}
	}

	/**
	 * Crea los descansos de los equipos que no juegan y borra los de los que
	 * ahora sí traen partido.
	 *
	 * @return {creados, eliminados}
	 */
	private int[] sincronizarDescansos(PrevisualizacionFfcv resuelta, Set<Long> equiposConPartido) {

		if (resuelta.desde() == null) {
			return new int[] { 0, 0 };
		}

		List<DescansoEquipo> actuales = descansoEquipoDao.obtenerPorRangoFechas(resuelta.desde(), resuelta.hasta());

		int eliminados = 0;
		Set<Long> equiposConDescanso = new HashSet<>();

		for (DescansoEquipo descanso : actuales) {
			if (equiposConPartido.contains(descanso.getEquipoId())) {
				descansoEquipoDao.eliminar(descanso.getId());
				eliminados++;
			} else {
				equiposConDescanso.add(descanso.getEquipoId());
			}
		}

		int creados = 0;

		for (FfcvDescanso descansa : resuelta.descansan()) {
			if (equiposConDescanso.add(descansa.equipoId())) {
				descansoEquipoDao.crear(new DescansoEquipo(null, descansa.equipoId(), resuelta.desde(), null));
				creados++;
			}
		}

		return new int[] { creados, eliminados };
	}

	private List<String> construirAvisos(PrevisualizacionFfcv resuelta) {

		List<String> avisos = new ArrayList<>();

		for (FfcvFilaPrevisualizacion fila : resuelta.filas()) {
			if (fila.estado() == EstadoFilaFfcv.CONFLICTO_MANUAL) {
				avisos.add(fila.equipoNombre() + ": el " + fila.fecha().format(FORMATO_FECHA)
						+ " ya hay un partido manual, no se ha importado.");
			} else if (fila.estado() == EstadoFilaFfcv.SIN_EQUIPO) {
				avisos.add("Sin equipo asignado, no se ha importado: " + fila.competicion()
						+ (fila.letra() != null ? " \"" + fila.letra() + "\"" : "") + ".");
			}
		}

		/*
		 * Los partidos que ya no aparecen no se borran ni se mueven nunca:
		 * se dejan como aviso (con mención especial si tienen convocatoria).
		 */
		for (FfcvDesaparecido desaparecido : resuelta.desaparecidos()) {
			avisos.add(desaparecido.equipoNombre() + ": el partido del "
					+ desaparecido.fecha().format(FORMATO_FECHA) + " contra " + desaparecido.rival()
					+ " ya no aparece en la FFCV (¿aplazado?); no se ha tocado"
					+ (desaparecido.tieneConvocatoria() ? " y tiene convocatoria." : "."));
		}

		return avisos;
	}

	private static Partido construirPartido(FfcvFilaPrevisualizacion fila) {

		Partido partido = new Partido();

		partido.setId(fila.partidoId());
		partido.setEquipoId(fila.equipoId());
		partido.setRival(fila.rival());
		partido.setDia(java.sql.Date.valueOf(fila.fecha()));
		partido.setHora(fila.hora());
		partido.setCampo(fila.campo());
		partido.setJornada(fila.jornada());
		partido.setLocal(fila.local());
		partido.setTipo(TIPO_LIGA);
		partido.setOrigen(Partido.ORIGEN_FFCV);

		return partido;
	}

	/*
	 * Resuelve cada fila del archivo contra EQUIPOS_FFCV y PARTIDOS, y
	 * calcula los equipos que descansan y los partidos que han desaparecido.
	 * No escribe nada.
	 */
	private PrevisualizacionFfcv resolver(List<FfcvFilaCsv> filas, List<FfcvErrorLinea> erroresBase) {

		List<FfcvErrorLinea> errores = new ArrayList<>(
				erroresBase.size() > MAXIMO_ERRORES ? erroresBase.subList(0, MAXIMO_ERRORES) : erroresBase);

		if (filas.isEmpty()) {
			return new PrevisualizacionFfcv(null, null, List.of(), List.of(), List.of(), errores);
		}

		LocalDate desde = filas.stream().map(FfcvFilaCsv::fecha).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate hasta = filas.stream().map(FfcvFilaCsv::fecha).max(Comparator.naturalOrder()).orElseThrow();

		Map<Long, Equipo> equipos = new HashMap<>();
		for (Equipo equipo : equipoDao.obtenerTodos()) {
			equipos.put(equipo.getId(), equipo);
		}

		Map<String, Long> relaciones = new HashMap<>();
		for (EquipoFfcv relacion : equipoFfcvDao.listarTodos()) {
			relaciones.put(clave(relacion.getCompeticion(), relacion.getLetra()), relacion.getEquipoId());
		}

		Map<String, Partido> ffcvExistentes = new HashMap<>();
		Map<String, Partido> manualesExistentes = new HashMap<>();

		for (Partido partido : partidoDao.obtenerPorRangoFechas(desde, hasta)) {
			if (partido.getDia() == null) {
				continue;
			}
			String clavePartido = clavePartido(partido.getEquipoId(), aLocalDate(partido.getDia()));
			if (Partido.ORIGEN_FFCV.equals(partido.getOrigen())) {
				ffcvExistentes.putIfAbsent(clavePartido, partido);
			} else {
				manualesExistentes.putIfAbsent(clavePartido, partido);
			}
		}

		List<FfcvFilaPrevisualizacion> resueltas = new ArrayList<>();
		Set<String> clavesVistas = new HashSet<>();
		Set<Long> equiposEnArchivo = new HashSet<>();

		for (FfcvFilaCsv fila : filas) {

			Long equipoId = relaciones.get(clave(fila.competicion(), fila.letra()));

			if (equipoId == null || !equipos.containsKey(equipoId)) {
				resueltas.add(nuevaFila(fila, EstadoFilaFfcv.SIN_EQUIPO, null, null, null, List.of()));
				continue;
			}

			String nombreEquipo = equipos.get(equipoId).getNombre();
			String clavePartido = clavePartido(equipoId, fila.fecha());

			// La clave de un partido importado es equipo + día: no puede haber dos.
			if (!clavesVistas.add(clavePartido)) {
				errores.add(new FfcvErrorLinea(0, nombreEquipo + " aparece más de una vez el "
						+ fila.fecha().format(FORMATO_FECHA) + "; se ignora la repetición"));
				continue;
			}

			equiposEnArchivo.add(equipoId);

			Partido ffcv = ffcvExistentes.get(clavePartido);

			if (ffcv != null) {
				List<String> cambios = diferencias(ffcv, fila);
				resueltas.add(nuevaFila(fila,
						cambios.isEmpty() ? EstadoFilaFfcv.SIN_CAMBIOS : EstadoFilaFfcv.ACTUALIZADO,
						equipoId, nombreEquipo, ffcv.getId(), cambios));
				continue;
			}

			Partido manual = manualesExistentes.get(clavePartido);

			if (manual != null) {
				resueltas.add(nuevaFila(fila, EstadoFilaFfcv.CONFLICTO_MANUAL,
						equipoId, nombreEquipo, manual.getId(), List.of()));
				continue;
			}

			resueltas.add(nuevaFila(fila, EstadoFilaFfcv.NUEVO, equipoId, nombreEquipo, null, List.of()));
		}

		// Descansan: equipos CON relación que no aparecen en el archivo. Los
		// equipos sin relación (Escuelita, Veteranos...) no se marcan nunca.
		Set<Long> relacionados = new LinkedHashSet<>(relaciones.values());
		List<FfcvDescanso> descansan = new ArrayList<>();

		for (Long equipoId : relacionados) {
			Equipo equipo = equipos.get(equipoId);
			if (equipo != null && !equiposEnArchivo.contains(equipoId)) {
				descansan.add(new FfcvDescanso(equipoId, equipo.getNombre()));
			}
		}

		descansan.sort(Comparator.comparing(FfcvDescanso::equipoNombre, String.CASE_INSENSITIVE_ORDER));

		// Desaparecidos: partidos FFCV del rango que ya no vienen en el archivo.
		List<FfcvDesaparecido> desaparecidos = new ArrayList<>();

		for (Map.Entry<String, Partido> entrada : ffcvExistentes.entrySet()) {
			if (!clavesVistas.contains(entrada.getKey())) {
				Partido partido = entrada.getValue();
				Equipo equipo = equipos.get(partido.getEquipoId());
				desaparecidos.add(new FfcvDesaparecido(
						partido.getId(),
						partido.getEquipoId(),
						equipo != null ? equipo.getNombre() : null,
						aLocalDate(partido.getDia()),
						partido.getRival(),
						partido.getHora(),
						convocatoriaDao.obtenerPorPartidoId(partido.getId()) != null));
			}
		}

		desaparecidos.sort(Comparator.comparing(FfcvDesaparecido::fecha)
				.thenComparing(d -> d.equipoNombre() == null ? "" : d.equipoNombre()));

		return new PrevisualizacionFfcv(desde, hasta, resueltas, descansan, desaparecidos, errores);
	}

	private static FfcvFilaPrevisualizacion nuevaFila(
			FfcvFilaCsv fila,
			EstadoFilaFfcv estado,
			Long equipoId,
			String equipoNombre,
			Long partidoId,
			List<String> cambios) {

		return new FfcvFilaPrevisualizacion(
				fila.fecha(), fila.competicion(), fila.letra(), fila.local(), fila.rival(),
				fila.campo(), fila.hora(), fila.jornada(),
				estado, equipoId, equipoNombre, partidoId, cambios);
	}

	private static List<String> diferencias(Partido existente, FfcvFilaCsv fila) {

		List<String> cambios = new ArrayList<>();

		anadirCambio(cambios, "Hora", existente.getHora(), fila.hora());
		anadirCambio(cambios, "Campo", existente.getCampo(), fila.campo());
		anadirCambio(cambios, "Rival", existente.getRival(), fila.rival());
		anadirCambio(cambios, "Jornada",
				existente.getJornada() != null ? existente.getJornada().toString() : null,
				fila.jornada() != null ? fila.jornada().toString() : null);
		if (existente.isLocal() != fila.local()) {
			cambios.add("Condición: " + (existente.isLocal() ? "local" : "visitante") + " → "
					+ (fila.local() ? "local" : "visitante"));
		}

		return cambios;
	}

	private static void anadirCambio(List<String> cambios, String campo, String antes, String despues) {

		String a = antes == null ? "" : antes.trim();
		String d = despues == null ? "" : despues.trim();

		if (!Objects.equals(a, d)) {
			cambios.add(campo + ": " + (a.isEmpty() ? "—" : a) + " → " + (d.isEmpty() ? "—" : d));
		}
	}

	/*
	 * La previsualización vuelve del navegador: se normaliza y valida todo
	 * lo que se va a escribir, sin fiarse del formato recibido.
	 */
	private static FfcvFilaCsv normalizarFila(FfcvFilaCsv fila) {

		if (fila.fecha() == null) {
			throw new IllegalArgumentException("Hay una fila sin fecha");
		}

		String competicion = FfcvCsvParser.normalizarCompeticion(fila.competicion());

		if (competicion.isEmpty()) {
			throw new IllegalArgumentException("Hay una fila sin competición");
		}

		String rival = limitar(fila.rival());

		if (rival == null || rival.isEmpty()) {
			throw new IllegalArgumentException("Hay una fila sin rival");
		}

		return new FfcvFilaCsv(
				fila.fecha(),
				competicion,
				normalizarLetra(fila.letra()),
				fila.local(),
				rival,
				vacioANull(limitar(fila.campo())),
				vacioANull(fila.hora() == null ? null : fila.hora().trim()),
				fila.jornada());
	}

	private static String limitar(String texto) {

		if (texto == null) {
			return null;
		}

		String limpio = texto.trim();

		return limpio.length() > LONGITUD_MAXIMA_TEXTO ? limpio.substring(0, LONGITUD_MAXIMA_TEXTO) : limpio;
	}

	private static String vacioANull(String texto) {
		return texto == null || texto.isEmpty() ? null : texto;
	}

	static String normalizarLetra(String letra) {
		return letra == null || letra.isBlank() ? null : letra.trim().toUpperCase();
	}

	private static String clave(String competicion, String letra) {
		return competicion + "|" + (letra == null ? "" : letra);
	}

	private static String clavePartido(Long equipoId, LocalDate fecha) {
		return equipoId + "|" + fecha;
	}

	private static LocalDate aLocalDate(Date fecha) {

		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}

		return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

}
