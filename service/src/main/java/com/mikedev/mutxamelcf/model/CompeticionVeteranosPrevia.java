package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Una competición de la LVCA en la que aparece el Mutxamel (la Liga, la "Copa grupo C"...): su nombre (el de la pestaña),
 * la tabla completa y lo que haría falta crear al importarla.
 *
 * @param jornada         la web no la da: es el máximo de PJ de la tabla
 * @param posicionNuestra la posición del Mutxamel en la tabla
 * @param clubesACrear    nombres de los equipos de esta tabla que todavía no tienen club en CLUBES_RIVALES (se crean sin
 *                        escudo al confirmar)
 */
public record CompeticionVeteranosPrevia(
        String nombre,
        int jornada,
        int posicionNuestra,
        List<FilaClasificacionVeteranos> tabla,
        List<String> clubesACrear) {
}
