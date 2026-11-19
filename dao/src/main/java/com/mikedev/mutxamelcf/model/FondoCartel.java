package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * El fondo de un cartel guardado en IMAGENES_REDES_FONDOS.
 *
 * @param cartel           PRIMER_EQUIPO o FILIAL
 * @param imagen           el JPG o PNG tal como se subió
 * @param tipoContenido    image/jpeg o image/png
 * @param usuarioActualizo quién lo subió (null si no se sabe)
 */
public record FondoCartel(String cartel, byte[] imagen, String tipoContenido, int ancho, int alto,
        LocalDateTime fechaActualizacion, String usuarioActualizo) {
}
