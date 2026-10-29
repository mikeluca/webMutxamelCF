package com.mikedev.mutxamelcf.service;

import java.util.List;

/**
 * Las cuotas de un jugador en la temporada activa que se crean y se retiran solas con el reparto de septiembre
 * (docs/transicion-temporada.md, decisiones 24 a 26).
 */
public interface CuotasTemporadaService {

	/**
	 * Crea las cuotas del jugador para cada concepto activo de la temporada activa, con el mismo cálculo de periodos
	 * que "Asignar cuotas" (los mensuales, de septiembre a junio, o desde el mes de la asignación si es posterior a
	 * septiembre) y sin duplicar las que ya tiene.
	 *
	 * @return cuántas cuotas se han creado
	 */
	int crearCuotasDelJugador(Long jugadorId);

	/**
	 * Borra las cuotas de la temporada activa del jugador que no tienen ningún pago. Las que tienen pagos no se tocan.
	 */
	ResultadoRetiradaCuotas retirarCuotasDelJugador(Long jugadorId);

	/**
	 * @param borradas              cuotas sin pagos que se han borrado
	 * @param conservadasConPagos   cuotas que se quedan porque tienen algún pago
	 */
	record ResultadoRetiradaCuotas(int borradas, int conservadasConPagos, List<Long> cuotasConservadas) {
	}
}
