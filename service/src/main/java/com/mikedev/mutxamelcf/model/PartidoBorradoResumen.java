package com.mikedev.mutxamelcf.model;

/**
 * Lo que se borrará junto con un partido (GET .../resumen-borrado), para la confirmación del panel y de la app.
 *
 * @param partidoId            el partido
 * @param tieneDatos           true si tiene convocatoria, resultado, retransmisión, alineación o estadísticas
 * @param puedeBorrar          si quien pregunta puede borrarlo (un entrenador no si {@code tieneDatos})
 * @param tieneConvocatoria    si tiene convocatoria
 * @param convocados           jugadores de esa convocatoria, invitados incluidos (0 sin convocatoria)
 * @param tieneResultado       si tiene resultado (goles o texto distinto de "-")
 * @param eventosRetransmision eventos de la retransmisión, anulados incluidos
 * @param tieneAlineacion      si tiene alineación guardada
 * @param jugadoresAlineacion  jugadores de esa alineación, titulares y suplentes
 * @param filasEstadisticas    filas de estadísticas de jugador (una por jugador)
 */
public record PartidoBorradoResumen(
		Long partidoId,
		boolean tieneDatos,
		boolean puedeBorrar,
		boolean tieneConvocatoria,
		int convocados,
		boolean tieneResultado,
		int eventosRetransmision,
		boolean tieneAlineacion,
		int jugadoresAlineacion,
		int filasEstadisticas) {
}
