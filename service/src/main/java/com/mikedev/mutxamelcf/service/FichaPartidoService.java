package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.FichaPartidoDTO;

/** Ficha pública de un partido del Primer Equipo. */
public interface FichaPartidoService {

    /**
     * La ficha del partido, con los textos fijos en {@code idioma} (es, ca, en; null = castellano). Null si el
     * partido no existe o no es del Primer Equipo.
     */
    FichaPartidoDTO obtenerFicha(Long partidoId, String idioma);
}
