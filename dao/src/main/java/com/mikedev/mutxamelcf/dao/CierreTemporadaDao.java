package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;

import com.mikedev.mutxamelcf.model.CifrasCierre;

/**
 * Las sentencias de "Cerrar temporada" que no son de un solo jugador. Las llama el servicio dentro de la misma
 * transacción que el resto del cierre. "Equipo de adultos" es el de una categoría que no está en CATEGORIAS_EDAD
 * (Filial, Primer Equipo, Veteranos).
 */
public interface CierreTemporadaDao {

	/** Lo que haría el cierre, sin tocar nada. */
	CifrasCierre cifras(Long origenId, Long destinoId, LocalDate inicioDestino);

	/** Paso 1: borra e inserta la foto del cuerpo técnico de la temporada que se cierra. Devuelve las filas insertadas. */
	int fotografiarCuerpoTecnico(Long origenId);

	/**
	 * Paso 2: EQUIPO_TEMPORADA de la temporada nueva para todos los equipos que aún no tienen fila: activos los de
	 * adultos y con ACTIVO = 0 el resto (misma modalidad y tipo que en la temporada que se cierra). Los equipos de
	 * adultos que ya tuvieran fila se dejan activos. Devuelve las filas creadas.
	 */
	int copiarEquipos(Long origenId, Long destinoId);

	/** El equipo con ese nombre o, si no existe, uno nuevo (grupo P, deporte F). Devuelve su ID. */
	Long obtenerOCrearEquipo(String nombre, String categoria, String grupo, String orden, String deporte,
			String modalidad);

	/** Deja el equipo activo en la temporada como PRETEMPORADA con esa modalidad (crea o actualiza su fila). */
	void activarPretemporada(Long equipoId, Long destinoId, String modalidad);

	/** Paso 4: borra CUERPO_TECNICO_EQUIPO salvo los equipos de adultos. Devuelve las filas borradas. */
	int desasignarCuerpoTecnico();

	/** Paso 5a: copia a la temporada nueva, con ACTIVO = 0, los horarios activos de la que se cierra. */
	int copiarHorariosActivos(Long origenId, Long destinoId);

	/** Paso 5b: desactiva los horarios activos de la temporada que se cierra. */
	int desactivarHorarios(Long origenId);

	/**
	 * Paso 5c: borra las sesiones de entrenamiento con fecha igual o posterior a {@code desde} que no tienen
	 * entrenamiento, y antes las justificaciones de falta que las referencian. Devuelve las sesiones borradas.
	 */
	int eliminarSesionesSinEntrenamiento(LocalDate desde);

	/** Paso 6: copia los conceptos de pago activos que todavía no están (por nombre) en la temporada nueva. */
	int copiarConceptosActivos(Long origenId, Long destinoId);

	/** Paso 7: borra todas las notificaciones de la app. */
	int eliminarNotificaciones();
}
