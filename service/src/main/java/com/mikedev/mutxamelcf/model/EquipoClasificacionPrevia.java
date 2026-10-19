package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Qué pasaría con la clasificación de un equipo del club si se confirmara la
 * importación. {@code tabla} es la tabla COMPLETA del grupo (solo si está
 * encontrado) con ES_NUESTRO ya marcado, lista para insertar.
 *
 * @param encontrado         el equipo aparece en el archivo (y su tabla es legible)
 * @param motivo             por qué no se ha encontrado (solo si no lo está)
 * @param antiguo            la jornada del archivo es menor que la guardada
 * @param importable         se puede confirmar este equipo
 * @param marcadoPorDefecto  casilla activa al abrir la vista previa
 * @param cambia             hay diferencias respecto a lo guardado
 * @param cambios            descripción de las diferencias
 */
public record EquipoClasificacionPrevia(
        Long equipoId,
        String equipoNombre,
        boolean encontrado,
        String motivo,
        String competicion,
        Integer jornada,
        Integer jornadaGuardada,
        Integer posicion,
        Integer puntos,
        Integer pj,
        boolean antiguo,
        boolean importable,
        boolean marcadoPorDefecto,
        boolean cambia,
        List<String> cambios,
        List<Clasificacion> tabla) {
}
