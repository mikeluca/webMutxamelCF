package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * El historial de una temporada: las plantillas por equipo (JUGADOR_TEMPORADA), el cuerpo técnico
 * (CUERPO_TECNICO_TEMPORADA), la última clasificación guardada de cada equipo y, si se ha elegido un equipo, sus
 * estadísticas con las fechas de esa temporada.
 *
 * @param equiposConDatos los equipos que aparecen en las plantillas de la temporada (para elegir las estadísticas)
 * @param estadisticas    las del equipo elegido, o nulo
 */
public record HistorialTemporadaDTO(
		Temporada temporada,
		List<PlantillaEquipo> plantillas,
		List<StaffEquipo> cuerpoTecnico,
		List<TablaClasificacion> clasificaciones,
		List<EquipoOpcion> equiposConDatos,
		EstadisticasEquipo estadisticas) {

	public record PlantillaEquipo(String equipo, List<JugadorPlantilla> jugadores) {
	}

	public record JugadorPlantilla(Long jugadorId, String nombre, Integer dorsal, String estado, Integer anioNacimiento) {
	}

	public record StaffEquipo(String equipo, List<PersonaStaff> personas) {
	}

	public record PersonaStaff(String nombre, String puesto) {
	}

	public record TablaClasificacion(Long equipoId, String equipo, String competicion, Integer jornada,
			List<ClasificacionFilaPublicDTO> filas) {
	}

	public record EquipoOpcion(Long equipoId, String nombre) {
	}

	/** {@code sesiones} son los entrenamientos del equipo en la temporada; {@code sesionesRevisadas}, con la lista pasada. */
	public record EstadisticasEquipo(Long equipoId, String equipo, PartidosEquipo partidos, int sesiones,
			int sesionesRevisadas, List<JugadorEstadisticas> jugadores) {
	}

	public record JugadorEstadisticas(Long jugadorId, String nombre, Integer dorsal, int partidos, int goles,
			int asistencias, int amarillas, int rojas, int entrenamientos, int asistidos) {
	}
}
