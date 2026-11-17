package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.JugadorEquipoAppDTO;

/** Edición del dorsal y la posición de un jugador desde la app (entrenador de su equipo, coordinación y ADMIN_APP). */
public interface JugadorDorsalPosicionService {

    /**
     * Guarda solo el dorsal y la posición del jugador (nulos = vacío) y, si existe, el dorsal de su fila de
     * JUGADOR_TEMPORADA de la temporada activa. Devuelve el jugador actualizado.
     *
     * @param posicionCodigo PORTERO, DEFENSA, CENTROCAMPISTA, EXTREMO, DELANTERO o null
     * @throws SecurityException si no puede gestionar el equipo del jugador (sin dar detalles del jugador)
     * @throws java.util.NoSuchElementException si el jugador no existe o está de baja
     * @throws IllegalArgumentException si el dorsal no está entre 1 y 99 o la posición no es de la lista
     * @throws com.mikedev.mutxamelcf.model.DorsalRepetidoException si otro jugador activo del equipo ya lo tiene
     */
    JugadorEquipoAppDTO actualizar(Long usuarioAppId, Long jugadorId, Integer dorsal, String posicionCodigo);
}
