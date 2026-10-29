package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.CuerpoTecnicoTemporadaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.ClasificacionFilaPublicDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoTemporadaFila;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.EquipoOpcion;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.EstadisticasEquipo;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.JugadorEstadisticas;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.JugadorPlantilla;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.PersonaStaff;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.PlantillaEquipo;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.StaffEquipo;
import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO.TablaClasificacion;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.HistorialTemporadaService;

@Service
public class HistorialTemporadaServiceImpl implements HistorialTemporadaService {

	private final TemporadaDao temporadaDao;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final CuerpoTecnicoTemporadaDao cuerpoTecnicoTemporadaDao;
	private final ClasificacionDao clasificacionDao;
	private final EquipoDao equipoDao;
	private final ResumenTemporadaDao resumenTemporadaDao;
	private final Clock clock;

	public HistorialTemporadaServiceImpl(TemporadaDao temporadaDao, JugadorTemporadaDao jugadorTemporadaDao,
			CuerpoTecnicoTemporadaDao cuerpoTecnicoTemporadaDao, ClasificacionDao clasificacionDao,
			EquipoDao equipoDao, ResumenTemporadaDao resumenTemporadaDao, Clock clock) {
		this.temporadaDao = temporadaDao;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.cuerpoTecnicoTemporadaDao = cuerpoTecnicoTemporadaDao;
		this.clasificacionDao = clasificacionDao;
		this.equipoDao = equipoDao;
		this.resumenTemporadaDao = resumenTemporadaDao;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Temporada> temporadas() {
		return temporadaDao.obtenerTodos().stream()
				.sorted(Comparator.comparing(Temporada::getFechaInicio, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public HistorialTemporadaDTO obtener(Long temporadaId, Long equipoId) {
		Temporada temporada = temporadaId == null ? null : temporadaDao.obtenerPorId(temporadaId);

		if (temporada == null) {
			throw new IllegalArgumentException("La temporada no existe.");
		}

		List<JugadorTemporada> filas = jugadorTemporadaDao.listarPorTemporada(temporadaId);
		Map<Long, String> nombresDeEquipo = new HashMap<>();
		equipoDao.obtenerTodos().forEach(e -> nombresDeEquipo.put(e.getId(), e.getNombre()));

		return new HistorialTemporadaDTO(temporada, plantillas(filas), cuerpoTecnico(temporadaId),
				clasificaciones(temporadaId, nombresDeEquipo), equiposConDatos(filas),
				equipoId == null ? null : estadisticas(temporada, equipoId, filas, nombresDeEquipo));
	}

	private static String nombre(String nombre, String apellidos) {
		return ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
	}

	// ---- plantillas ----------------------------------------------------------------------------

	private List<PlantillaEquipo> plantillas(List<JugadorTemporada> filas) {
		Map<String, List<JugadorPlantilla>> porEquipo = new LinkedHashMap<>();

		for (JugadorTemporada f : filas) {
			String clave = f.getEquipoNombre() != null ? f.getEquipoNombre()
					: (f.getCategoria() != null ? f.getCategoria() + " (sin equipo)" : "Sin equipo");

			porEquipo.computeIfAbsent(clave, k -> new ArrayList<>()).add(new JugadorPlantilla(f.getJugadorId(),
					nombre(f.getJugadorNombre(), f.getJugadorApellidos()), f.getDorsal(), f.getEstado(),
					f.getAnioNacimiento()));
		}

		return porEquipo.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(e -> new PlantillaEquipo(e.getKey(), e.getValue())).toList();
	}

	private List<EquipoOpcion> equiposConDatos(List<JugadorTemporada> filas) {
		Map<Long, String> equipos = new LinkedHashMap<>();
		filas.stream().filter(f -> f.getEquipoId() != null)
				.forEach(f -> equipos.putIfAbsent(f.getEquipoId(), f.getEquipoNombre()));

		return equipos.entrySet().stream().map(e -> new EquipoOpcion(e.getKey(), e.getValue()))
				.sorted(Comparator.comparing(EquipoOpcion::nombre, Comparator.nullsLast(String::compareTo))).toList();
	}

	// ---- cuerpo técnico --------------------------------------------------------------------------

	private List<StaffEquipo> cuerpoTecnico(Long temporadaId) {
		Map<String, List<PersonaStaff>> porEquipo = new LinkedHashMap<>();

		for (CuerpoTecnicoTemporadaFila f : cuerpoTecnicoTemporadaDao.listarPorTemporada(temporadaId)) {
			porEquipo.computeIfAbsent(f.equipoNombre(), k -> new ArrayList<>())
					.add(new PersonaStaff(nombre(f.nombre(), f.apellidos()), f.puesto()));
		}

		return porEquipo.entrySet().stream().map(e -> new StaffEquipo(e.getKey(), e.getValue())).toList();
	}

	// ---- clasificaciones ---------------------------------------------------------------------------

	private List<TablaClasificacion> clasificaciones(Long temporadaId, Map<Long, String> nombresDeEquipo) {
		Map<Long, List<Clasificacion>> porEquipo = clasificacionDao.listarPorTemporada(temporadaId).stream()
				.collect(Collectors.groupingBy(Clasificacion::getEquipoId, LinkedHashMap::new, Collectors.toList()));
		List<TablaClasificacion> tablas = new ArrayList<>();

		porEquipo.forEach((equipoId, filas) -> {
			// La competición y la jornada son las de la fila del club
			Clasificacion referencia = filas.stream().filter(Clasificacion::isEsNuestro).findFirst().orElse(filas.get(0));

			tablas.add(new TablaClasificacion(equipoId, nombresDeEquipo.getOrDefault(equipoId, "Equipo " + equipoId),
					referencia.getCompeticion(), referencia.getJornada(),
					filas.stream().map(f -> new ClasificacionFilaPublicDTO(f.getPosicion(), f.getNombreEquipo(),
							f.isEsNuestro(), f.getPuntos(), f.getPj(), f.getPg(), f.getPe(), f.getPp(), f.getGf(),
							f.getGc())).toList()));
		});

		return tablas.stream().sorted(Comparator.comparing(TablaClasificacion::equipo)).toList();
	}

	// ---- estadísticas -----------------------------------------------------------------------------

	private EstadisticasEquipo estadisticas(Temporada temporada, Long equipoId, List<JugadorTemporada> filas,
			Map<Long, String> nombresDeEquipo) {
		LocalDate hoy = LocalDate.now(clock);
		LocalDate desde = aLocalDate(temporada.getFechaInicio());
		LocalDate hasta = temporada.getFechaFin() == null ? hoy : aLocalDate(temporada.getFechaFin());

		// Una temporada pasada cuenta todos sus partidos; la que está en curso, solo los ya jugados
		LocalDate limite = hoy.isAfter(hasta) ? hasta.plusDays(1) : hoy;

		Map<Long, AsistenciaJugadorEquipo> asistencia = resumenTemporadaDao
				.asistenciaEntrenamientosDelEquipo(equipoId, desde, hasta).stream()
				.collect(Collectors.toMap(AsistenciaJugadorEquipo::jugadorId, a -> a, (a, b) -> a));

		List<JugadorEstadisticas> jugadores = new ArrayList<>();

		for (EstadisticasJugadorEquipo j : resumenTemporadaDao.estadisticasJugadoresDelEquipo(equipoId, desde, hasta,
				limite)) {
			AsistenciaJugadorEquipo a = asistencia.get(j.jugadorId());

			jugadores.add(new JugadorEstadisticas(j.jugadorId(), j.nombre(), j.dorsal(), j.partidos(), j.goles(),
					j.asistencias(), j.amarillas(), j.rojas(), a == null ? 0 : a.total(), a == null ? 0 : a.asistidos()));
		}

		jugadores.sort(Comparator.comparing(JugadorEstadisticas::goles).reversed()
				.thenComparing(JugadorEstadisticas::nombre));

		String nombreEquipo = filas.stream().filter(f -> equipoId.equals(f.getEquipoId()))
				.map(JugadorTemporada::getEquipoNombre).findFirst().orElse(nombresDeEquipo.get(equipoId));

		return new EstadisticasEquipo(equipoId, nombreEquipo,
				resumenTemporadaDao.partidosDelEquipo(equipoId, desde, hasta),
				resumenTemporadaDao.contarEntrenamientosDelEquipo(equipoId, desde, hasta, false),
				resumenTemporadaDao.contarEntrenamientosDelEquipo(equipoId, desde, hasta, true), jugadores);
	}

	private static LocalDate aLocalDate(java.util.Date fecha) {
		return fecha instanceof java.sql.Date sql ? sql.toLocalDate()
				: fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}
}
