package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;

/**
 * El cartel "Próximo partido del Filial", generado en el servidor al momento (no se guarda). Mismos permisos, respuestas,
 * códigos y parámetros que {@link CartelPrimerEquipoService}: solo RETRANSMISION y ADMIN_APP.
 */
public interface CartelFilialService {

    /** El próximo partido del Filial y sus datos, o {@code disponible = false} con el motivo (SIN_PARTIDO o YA_EMPEZADO). */
    PartidoPrimerEquipoResponse partido(Long usuarioAppId);

    /**
     * El PNG del cartel.
     *
     * @param formato  PUBLICACION (1080×1350) o HISTORIA (1080×1920)
     * @param idioma   es o ca
     * @throws CartelNoDisponibleException si no hay partido o ya ha empezado
     * @throws IllegalArgumentException    si el formato o el idioma no son válidos
     */
    byte[] png(Long usuarioAppId, String formato, String idioma, boolean racha, boolean qr, boolean hashtags);
}
