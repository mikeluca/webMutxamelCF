package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deja pasar un aviso salvo que el anterior con la misma clave (p. ej.
 * "partido:jugador") fuera del MISMO tipo y de hace menos del intervalo: un
 * cambio de estado siempre se avisa. Consultar ({@link #permitir}) y anotar el
 * envío ({@link #registrar}) son dos pasos, para anotarlo cuando la
 * transacción ha confirmado. El registro es en memoria: se pierde al reiniciar
 * y no se comparte entre instancias.
 */
final class LimitadorAvisos {

	private record Aviso(String tipo, LocalDateTime fecha) {
	}

	private final Clock clock;
	private final Duration intervalo;
	private final Map<String, Aviso> ultimos = new ConcurrentHashMap<>();

	LimitadorAvisos(Clock clock, Duration intervalo) {
		this.clock = clock;
		this.intervalo = intervalo;
	}

	/** true si no consta un aviso de este tipo con esta clave hace menos del intervalo. No registra nada. */
	boolean permitir(String clave, String tipo) {

		Aviso anterior = ultimos.get(clave);

		return anterior == null
				|| !anterior.tipo().equals(tipo)
				|| !LocalDateTime.now(clock).isBefore(anterior.fecha().plus(intervalo));
	}

	/** Anota que se ha enviado un aviso de este tipo con esta clave, ahora. */
	void registrar(String clave, String tipo) {

		LocalDateTime ahora = LocalDateTime.now(clock);

		ultimos.values().removeIf(aviso -> !ahora.isBefore(aviso.fecha().plus(intervalo)));
		ultimos.put(clave, new Aviso(tipo, ahora));
	}

	/** El tipo del último aviso anotado para la clave (aunque haya pasado el intervalo); null si no hay. */
	String ultimoTipo(String clave) {

		Aviso aviso = ultimos.get(clave);

		return aviso == null ? null : aviso.tipo();
	}
}
