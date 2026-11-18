package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;

/** El correo de los avisos de cuenta. Lo implementa el módulo web, que es quien tiene el servidor de correo. */
public interface AvisosCuentaEmail {

	/**
	 * Avisa a la cuenta de que se desactivará el {@code fechaDesactivacion} si no vuelve a tener un jugador activo, en
	 * el {@code idioma} de la cuenta ("es", "ca" o "en").
	 */
	boolean enviarAvisoCuentaSinJugadores(String email, String nombrePersona, LocalDate fechaDesactivacion,
			String idioma);

	/**
	 * La solicitud de acceso se ha aprobado (primer hijo aprobado): "ya tienes acceso", con los jugadores aprobados en ese
	 * momento y lo que puede hacer una familia en la app.
	 */
	boolean enviarSolicitudFamiliaAprobada(String email, String nombrePersona, java.util.List<String> jugadores,
			String idioma);

	/** Se aprueba otro hijo de la misma solicitud: email corto. */
	boolean enviarSolicitudFamiliaOtroAprobado(String email, String nombrePersona, String jugador, String idioma);

	/** Se rechaza un hijo; {@code motivo} (puede ser null o vacío) solo se añade si el revisor escribió uno. */
	boolean enviarSolicitudFamiliaRechazada(String email, String nombrePersona, String jugador, String motivo,
			String idioma);
}
