package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Las imágenes para redes de un fin de semana (de viernes a domingo) y los textos sugeridos para publicarlas. */
public record ImagenesRedesResponse(
        LocalDate desde,
        LocalDate hasta,
        List<Imagen> imagenes,
        String textoPrevia,
        String textoResultados) {

    /** Una imagen generada: se descarga con GET /api/app/imagenes-redes/{id}/png (version sirve para la caché). */
    public record Imagen(
            Long id,
            String tipo,
            String modalidad,
            int pagina,
            int totalPaginas,
            int version,
            LocalDateTime fechaGeneracion) {
    }
}
