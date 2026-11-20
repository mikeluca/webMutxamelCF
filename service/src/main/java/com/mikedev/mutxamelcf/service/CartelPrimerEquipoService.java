package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;

/**
 * El cartel "Próximo partido del Primer Equipo", generado en el servidor al momento (no se guarda). Solo para
 * RRSS y ADMIN_APP ({@link SecurityException} al resto, también a COORDINADOR).
 */
public interface CartelPrimerEquipoService {

    /** El próximo partido y sus datos, o {@code disponible = false} con el motivo (SIN_PARTIDO o YA_EMPEZADO). */
    PartidoPrimerEquipoResponse partido(Long usuarioAppId);

    /**
     * El PNG del cartel.
     *
     * @param formato   PUBLICACION (1080×1350) o HISTORIA (1080×1920)
     * @param idioma    es o ca
     * @param racha     con los últimos 5 resultados
     * @param qr        con el QR de la app
     * @param hashtags  con los hashtags
     * @throws CartelNoDisponibleException  si no hay partido o ya ha empezado
     * @throws IllegalArgumentException si el formato o el idioma no son válidos
     */
    byte[] png(Long usuarioAppId, String formato, String idioma, boolean racha, boolean qr, boolean hashtags);
}
