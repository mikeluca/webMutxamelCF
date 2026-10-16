package com.mikedev.mutxamelcf.model;

import java.util.List;

public record ResultadoImportacionFfcv(
        int creados,
        int actualizados,
        int sinCambios,
        int conflictosManuales,
        int sinEquipo,
        int descansosCreados,
        int descansosEliminados,
        List<String> avisos) {
}
