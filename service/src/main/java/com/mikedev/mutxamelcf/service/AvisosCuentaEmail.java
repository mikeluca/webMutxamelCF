package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;

/** El correo de los avisos de cuenta. Lo implementa el módulo web, que es quien tiene el servidor de correo. */
public interface AvisosCuentaEmail {

	/** Avisa a la cuenta de que se desactivará el {@code fechaDesactivacion} si no vuelve a tener un jugador activo. */
	boolean enviarAvisoCuentaSinJugadores(String email, String nombrePersona, LocalDate fechaDesactivacion);
}
