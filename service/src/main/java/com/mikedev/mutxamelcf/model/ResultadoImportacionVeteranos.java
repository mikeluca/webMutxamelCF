package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * @param competiciones  cuántas clasificaciones se han guardado
 * @param filas          filas guardadas en total
 * @param nombres        los nombres de las competiciones importadas
 * @param clubesCreados  los clubes rivales creados (sin escudo: se sube desde "Escudos de rivales")
 */
public record ResultadoImportacionVeteranos(
        int competiciones,
        int filas,
        List<String> nombres,
        List<String> clubesCreados) {
}
