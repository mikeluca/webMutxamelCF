package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.ConvocatoriaPartidoResponse;

public interface ConvocatoriaPartidoService {

    /**
     * Convocatoria del partido vista por un jugador o un familiar. El usuario debe estar
     * vinculado a ese jugador y el jugador debe ser del equipo del partido (si no,
     * {@link SecurityException}); sin convocatoria para el partido lanza
     * {@link java.util.NoSuchElementException}.
     */
    ConvocatoriaPartidoResponse obtenerParaJugador(Long usuarioAppId, Long partidoId, Long jugadorId);
}
