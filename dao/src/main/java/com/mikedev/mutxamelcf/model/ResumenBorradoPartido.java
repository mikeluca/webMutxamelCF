package com.mikedev.mutxamelcf.model;

/**
 * Lo que se borra junto con un partido (ver {@code PartidoDao#eliminar}), contado fila a fila.
 *
 * @param convocatoriaId ID de la convocatoria del partido; null si no tiene
 * @param convocados     filas de CONVOCATORIA_JUGADOR de esa convocatoria (propios e invitados)
 * @param eventos        filas de PARTIDO_LIVE_EVENTOS, anuladas incluidas
 * @param alineacion     filas de PARTIDO_ALINEACION (titulares y suplentes)
 * @param estadisticas   filas de PARTIDO_ESTADISTICAS_JUGADOR
 */
public record ResumenBorradoPartido(
		Long convocatoriaId, int convocados, int eventos, int alineacion, int estadisticas) {

	public boolean tieneConvocatoria() {
		return convocatoriaId != null;
	}
}
