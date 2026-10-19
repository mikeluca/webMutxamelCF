package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.MiConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.RespuestaConvocatoriaRequest;

/**
 * Lo que pueden hacer JUGADOR y FAMILIAR con las convocatorias: ver las de los
 * jugadores vinculados a su cuenta y responder "Va" / "No puede". No exige
 * poder gestionar el equipo, sino el vínculo con el jugador.
 */
public interface RespuestaConvocatoriaService {

    /** Días hacia delante que se muestran. */
    int DIAS_HACIA_DELANTE = 14;

    /**
     * Convocatorias de los jugadores vinculados al usuario con partido desde
     * hoy (aunque ya haya empezado) hasta dentro de {@link #DIAS_HACIA_DELANTE} días.
     */
    List<MiConvocatoriaResponse> obtenerMisConvocatorias(Long usuarioAppId);

    /**
     * @throws SecurityException        sin vínculo con el jugador
     * @throws IllegalArgumentException respuesta o motivo no válidos, el jugador
     *                                  no está en la convocatoria, el partido está
     *                                  cancelado o el plazo ha terminado
     */
    MiConvocatoriaResponse responder(
            Long usuarioAppId,
            Long convocatoriaId,
            Long jugadorId,
            RespuestaConvocatoriaRequest request);
}
