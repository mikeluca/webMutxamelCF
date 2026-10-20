package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deja pasar como mucho un aviso por clave (p. ej. "partido:jugador") cada
 * cierto intervalo, con el reloj inyectado. El registro es en memoria: se
 * pierde al reiniciar y no se comparte entre instancias, lo que basta para
 * evitar la avalancha de avisos de quien cambia de idea varias veces seguidas.
 */
final class LimitadorAvisos {

	private final Clock clock;
	private final Duration intervalo;
	private final Map<String, LocalDateTime> ultimos = new ConcurrentHashMap<>();

	LimitadorAvisos(Clock clock, Duration intervalo) {
		this.clock = clock;
		this.intervalo = intervalo;
	}

	/**
	 * true (y lo registra) si no hubo otro aviso con esta clave hace menos del
	 * intervalo. Un aviso omitido no reinicia la cuenta.
	 */
	boolean permitir(String clave) {

		LocalDateTime ahora = LocalDateTime.now(clock);
		LocalDateTime anterior = ultimos.get(clave);

		if (anterior != null && ahora.isBefore(anterior.plus(intervalo))) {
			return false;
		}

		ultimos.values().removeIf(fecha -> !ahora.isBefore(fecha.plus(intervalo)));
		ultimos.put(clave, ahora);

		return true;
	}
}
