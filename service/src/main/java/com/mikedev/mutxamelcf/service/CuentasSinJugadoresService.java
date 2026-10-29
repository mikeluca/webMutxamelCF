package com.mikedev.mutxamelcf.service;

/**
 * Cuentas de familia o de jugador sin ningún jugador activo (docs/transicion-temporada.md, decisión 27): se avisa
 * una vez, se desactivan a los 3 meses y se reactivan si vuelven a tener un jugador activo.
 */
public interface CuentasSinJugadoresService {

	/** Meses entre el aviso y la desactivación. */
	int MESES_HASTA_DESACTIVAR = 3;

	/** Una pasada del job diario. */
	ResultadoCuentasSinJugadores procesar();

	/**
	 * @param avisadas      cuentas avisadas hoy por primera vez
	 * @param desactivadas  cuentas desactivadas hoy (pasaron 3 meses desde el aviso)
	 * @param reactivadas   cuentas que desactivó el job y han vuelto a tener un jugador activo
	 * @param canceladas    avisos cancelados porque la cuenta volvió a tener un jugador activo (o la tomó un administrador)
	 */
	record ResultadoCuentasSinJugadores(int avisadas, int desactivadas, int reactivadas, int canceladas) {
	}
}
