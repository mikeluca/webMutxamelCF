package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuotaFamiliarResponse;

/**
 * Cuotas de los jugadores vinculados a una cuenta de la app (jugador
 * propio o hijo/a como familiar), para la pantalla "Cuotas".
 */
public interface CuotaFamiliarService {

    List<CuotaFamiliarResponse> obtenerCuotasDeMisJugadores(int usuarioAppId);
}
