package com.mikedev.mutxamelcf.dao;

import com.mikedev.mutxamelcf.model.PartidoLiveEstado;

/**
 * Marcador en directo del partido del primer equipo (una única fila,
 * reiniciada en cada "Inicio de partido"). Ver {@link PartidoLiveEstado}.
 */
public interface PartidoLiveDao {

    PartidoLiveEstado obtenerEstado();

    void reiniciar();

    void sumarGolFavor(String autor);

    void sumarGolContra();
}
