package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Lo que haría importar la página guardada de la clasificación de la LVCA. No escribe nada.
 *
 * @param equipoId       el equipo "Veteranos" del club al que se asignan las clasificaciones
 * @param competiciones  solo las tablas en las que aparece el Mutxamel, en el orden de las pestañas
 * @param clubesACrear   los clubes rivales que se crearían (sin repetir, en todas las competiciones)
 */
public record PrevisualizacionVeteranos(
        Long equipoId,
        String equipoNombre,
        List<CompeticionVeteranosPrevia> competiciones,
        List<String> clubesACrear) {
}
