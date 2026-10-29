package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.MorosidadDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;

/**
 * Morosidad (docs/transicion-temporada.md, decisión 25): las cuotas sin pagar de las temporadas que ya no están
 * activas, para cobrarlas, darlas por incobrables y exportarlas. Las familias no las ven.
 */
public interface MorosidadService {

	/** Las temporadas anteriores a la activa (o todas las no activas si no hay activa), la más reciente primero. */
	List<TemporadaDTO> temporadasAnteriores();

	/**
	 * Las cuotas sin pagar de la temporada indicada o, con null, de todas las anteriores, con los totales de cada
	 * temporada. Con [incluirCobradas] salen también las pagadas (estado visible "Cobrada").
	 *
	 * @throws IllegalArgumentException si la temporada no es una temporada anterior
	 */
	MorosidadDTO obtener(Long temporadaId, boolean incluirCobradas);

	/** Registra un pago por todo el importe pendiente, como en Pagos: la cuota queda PAGADA. */
	void cobrar(Long cuotaId);

	/** PENDIENTE o PARCIAL -> INCOBRABLE. */
	void marcarIncobrable(Long cuotaId);

	/** INCOBRABLE -> PENDIENTE, PARCIAL o PAGADA según sus pagos. */
	void deshacerIncobrable(Long cuotaId);

	/** Las mismas filas que {@link #obtener} en CSV (separador ";", importes con coma decimal). */
	String exportarCsv(Long temporadaId, boolean incluirCobradas);
}
