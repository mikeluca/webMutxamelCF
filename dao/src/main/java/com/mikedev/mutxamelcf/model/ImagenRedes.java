package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una imagen para redes ya generada (sin el PNG, que se lee aparte). */
public record ImagenRedes(
        Long id,
        String tipo,
        String modalidad,
        int pagina,
        int totalPaginas,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        LocalDateTime fechaGeneracion,
        int version) {
}
