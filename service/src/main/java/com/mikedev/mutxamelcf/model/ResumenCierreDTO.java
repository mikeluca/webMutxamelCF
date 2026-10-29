package com.mikedev.mutxamelcf.model;

import java.util.List;
import java.util.Map;

/**
 * Resumen de "Cerrar temporada": lo que va a pasar en cada paso y lo que lo bloquea. Si {@code bloqueos} no está
 * vacío no se puede cerrar.
 *
 * @param temporada           la temporada nueva (destino)
 * @param temporadaActual     la temporada que se cierra
 * @param jugadoresPorEquipo  jugadores que tendrá cada equipo o equipo de pretemporada al terminar el cierre
 * @param bajas               jugadores que se dan de baja
 * @param sinEquipo           jugadores activos que no van ni a un equipo ni a una pretemporada
 * @param pasos               recuentos por paso
 */
public record ResumenCierreDTO(
		String temporada,
		String temporadaActual,
		Map<String, Integer> jugadoresPorEquipo,
		int bajas,
		int sinEquipo,
		Pasos pasos,
		List<String> bloqueos) {

	public boolean puedeCerrar() {
		return bloqueos.isEmpty();
	}

	/**
	 * Un recuento por cada paso del cierre (números del 1 al 9).
	 *
	 * @param pretemporadaNuevos      equipos de pretemporada que se crean
	 * @param pretemporadaReutilizados equipos de pretemporada de otro año que se reutilizan
	 */
	public record Pasos(
			int fotoCuerpoTecnico,
			int equiposAdultosActivos,
			int equiposCopiadosInactivos,
			List<String> pretemporadaNuevos,
			List<String> pretemporadaReutilizados,
			int bajas,
			int pruebasQueEntran,
			int formativosAPretemporada,
			int adultosSeQuedan,
			int cuerpoTecnicoDesasignado,
			int horariosDesactivados,
			int horariosCopiados,
			int sesionesBorradas,
			int conceptosCopiados,
			int notificacionesBorradas,
			int destinatariosAviso) {
	}
}
