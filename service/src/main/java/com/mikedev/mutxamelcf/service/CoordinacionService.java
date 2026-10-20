package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;

import com.mikedev.mutxamelcf.model.FinDeSemanaResponse;
import com.mikedev.mutxamelcf.model.SeguimientoJugadoresResponse;

/** Paneles de coordinación. Solo COORDINADOR y ADMIN_APP (SecurityException al resto). */
public interface CoordinacionService {

    /** {@code fecha} es opcional: por defecto hoy. */
    FinDeSemanaResponse obtenerFinDeSemana(Long usuarioAppId, LocalDate fecha);

    /** {@code periodo}: "30d" (por defecto) o "temporada". */
    SeguimientoJugadoresResponse obtenerSeguimientoJugadores(Long usuarioAppId, String periodo);

    SeguimientoJugadoresResponse.Detalle obtenerDetalleSeguimiento(Long usuarioAppId, Long jugadorId, String periodo);
}
