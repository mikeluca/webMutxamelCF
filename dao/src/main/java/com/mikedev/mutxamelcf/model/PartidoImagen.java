package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/** Un partido con los datos que necesitan las imágenes para redes sociales. */
public record PartidoImagen(
        Long partidoId,
        Long equipoId,
        String equipo,
        String ordenEquipo,
        String modalidad,
        String rival,
        Long clubRivalId,
        LocalDate dia,
        String hora,
        String campo,
        boolean local,
        boolean cancelado,
        Integer golesFavor,
        Integer golesContra,
        String resultado) {
}
