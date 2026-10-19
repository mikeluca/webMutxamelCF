package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * @param importados  equipos cuya clasificación se ha sustituido
 * @param filas       filas de clasificación insertadas en total
 * @param equipos     nombres de los equipos importados
 * @param avisos      equipos marcados que no se han podido importar
 */
public record ResultadoImportacionClasificaciones(
        int importados,
        int filas,
        List<String> equipos,
        List<String> avisos) {
}
