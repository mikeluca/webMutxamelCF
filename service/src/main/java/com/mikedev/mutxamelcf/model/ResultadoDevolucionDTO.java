package com.mikedev.mutxamelcf.model;

/**
 * @param devueltos             jugadores que vuelven a la pretemporada
 * @param pretemporadaReactivada el equipo de pretemporada estaba desactivado (vacío) y vuelve a estar activo
 */
public record ResultadoDevolucionDTO(int devueltos, boolean pretemporadaReactivada) {
}
