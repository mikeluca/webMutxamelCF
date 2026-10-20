package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.Partido;

/**
 * Hasta cuándo se puede justificar (o retirar la justificación de) la
 * ausencia a un partido: hasta la hora de convocatoria del día del partido
 * si hay convocatoria con hora; si no, hasta la hora del partido; si no,
 * hasta las 00:00 del día del partido.
 */
final class PlazoJustificacionPartido {

	private static final Logger logger = LoggerFactory.getLogger(PlazoJustificacionPartido.class);

	private PlazoJustificacionPartido() {
	}

	/** El día del partido, o {@code null} si no tiene fecha. */
	static LocalDate dia(Partido partido) {

		if (partido == null || partido.getDia() == null) {
			return null;
		}

		return partido.getDia() instanceof java.sql.Date sqlDate
				? sqlDate.toLocalDate()
				: new java.sql.Date(partido.getDia().getTime()).toLocalDate();
	}

	/**
	 * El límite, o {@code null} si el partido no tiene fecha. [convocatoria]
	 * puede ser {@code null} (el partido todavía no tiene convocatoria).
	 */
	static LocalDateTime limite(Partido partido, Convocatoria convocatoria) {

		LocalDate dia = dia(partido);

		if (dia == null) {
			return null;
		}

		LocalTime hora = convocatoria == null
				? null
				: leer(convocatoria.getHoraConvocatoria(), "convocatoria", partido);

		if (hora == null) {
			hora = leer(partido.getHora(), "partido", partido);
		}

		return dia.atTime(hora != null ? hora : LocalTime.MIDNIGHT);
	}

	/** {@code null} si no hay hora o no se puede leer (en ese caso, con un aviso en el log). */
	private static LocalTime leer(String texto, String origen, Partido partido) {

		if (texto == null || texto.isBlank()) {
			return null;
		}

		LocalTime hora = HoraTexto.leer(texto);

		if (hora == null) {
			logger.warn("No se puede leer la hora '{}' ({}) del partido id={}: se usa otra hora o las 00:00",
					texto, origen, partido.getId());
		}

		return hora;
	}
}
