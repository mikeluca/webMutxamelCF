package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * @param filasLeidas     filas legibles de todo el archivo
 * @param competiciones   grupos distintos del archivo
 */
public record PrevisualizacionClasificaciones(
        int filasLeidas,
        int competiciones,
        List<EquipoClasificacionPrevia> equipos,
        List<ClasificacionSinMapeo> sinMapeo,
        List<FfcvErrorLinea> errores) {
}
