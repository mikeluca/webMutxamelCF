package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;
import java.util.List;

/** La pantalla de Morosidad: las cuotas de temporadas anteriores y los totales de cada una. */
public record MorosidadDTO(List<Fila> filas, List<TotalTemporada> totales) {

	/**
	 * @param estado        PENDIENTE, PARCIAL, INCOBRABLE o PAGADO
	 * @param estadoVisible lo que se muestra: el mismo estado, salvo PAGADO, que es "Cobrada"
	 */
	public record Fila(Long cuotaId, Long temporadaId, String temporada, Long jugadorId, String jugador, String equipo,
			String concepto, String periodo, BigDecimal importe, BigDecimal pagado, BigDecimal pendiente,
			String estado, String estadoVisible) {

		public boolean cobrada() {
			return "PAGADO".equals(estado);
		}

		public boolean incobrable() {
			return "INCOBRABLE".equals(estado);
		}
	}

	/**
	 * @param pendienteIncobrable la parte del pendiente que ya está marcada como incobrable
	 */
	public record TotalTemporada(Long temporadaId, String temporada, int cuotas, BigDecimal importe,
			BigDecimal pagado, BigDecimal pendiente, BigDecimal pendienteIncobrable) {
	}
}
