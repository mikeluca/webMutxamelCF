package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lectura tolerante de las horas que se guardan como texto (partidos, convocatorias, sesiones). */
final class HoraTexto {

	/** "H:mm", "HH:mm", "HH:mm:ss" y "HH.mm". */
	private static final Pattern HORA = Pattern.compile("(\\d{1,2})([:.])(\\d{2})(?::(\\d{2}))?");

	private HoraTexto() {
	}

	/** La hora, o {@code null} si el texto es nulo, vacío o no tiene una forma válida. */
	static LocalTime leer(String texto) {

		if (texto == null || texto.isBlank()) {
			return null;
		}

		Matcher matcher = HORA.matcher(texto.trim());

		if (!matcher.matches()) {
			return null;
		}

		int horas = Integer.parseInt(matcher.group(1));
		int minutos = Integer.parseInt(matcher.group(3));
		String textoSegundos = matcher.group(4);

		// Los segundos solo con ":" ("HH:mm:ss"); con "." solo "HH.mm".
		if (textoSegundos != null && !":".equals(matcher.group(2))) {
			return null;
		}

		int segundos = textoSegundos == null ? 0 : Integer.parseInt(textoSegundos);

		if (horas > 23 || minutos > 59 || segundos > 59) {
			return null;
		}

		return LocalTime.of(horas, minutos, segundos);
	}
}
