package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;

/**
 * Una cuota de una temporada anterior para la pantalla de Morosidad: quién la debe, en qué equipo estaba esa
 * temporada (JUGADOR_TEMPORADA), de qué concepto y periodo, cuánto era y cuánto se ha pagado.
 *
 * @param estado PENDIENTE, PARCIAL, INCOBRABLE o PAGADO
 */
public record CuotaMorosidad(
		Long cuotaId,
		Long temporadaId,
		String temporada,
		Long jugadorId,
		String jugador,
		String equipo,
		String concepto,
		String periodo,
		BigDecimal importe,
		BigDecimal pagado,
		String estado) {

	/** Lo que falta por pagar (nunca negativo). */
	public BigDecimal pendiente() {
		BigDecimal pendiente = (importe == null ? BigDecimal.ZERO : importe)
				.subtract(pagado == null ? BigDecimal.ZERO : pagado);

		return pendiente.signum() < 0 ? BigDecimal.ZERO : pendiente;
	}
}
