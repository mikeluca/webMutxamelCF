package com.mikedev.mutxamelcf.model;

import java.util.List;
import java.util.Set;

/**
 * @param filas             todas las filas legibles del archivo (de todas las competiciones)
 * @param errores           filas que no se han podido leer (nunca incluyen su contenido)
 * @param gruposConErrores  claves de grupo que tienen al menos una fila ilegible: su
 *                          tabla estaría incompleta y no debe importarse
 */
public record FfcvClasificacionesParseResult(
        List<FfcvFilaClasificacion> filas,
        List<FfcvErrorLinea> errores,
        Set<String> gruposConErrores) {
}
