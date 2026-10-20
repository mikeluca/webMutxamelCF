package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Pattern;

import com.mikedev.mutxamelcf.model.Partido;

/**
 * Cuándo un partido tiene resultado y cuándo se considera terminado. Lo comparten
 * el recordatorio de resultado pendiente (RecordatorioResultadoPartidoScheduler) y
 * el panel de coordinación, para que ambos hablen de los mismos partidos.
 */
final class CriterioResultadoPartido {

	/** Un partido se da por terminado a las 2 h de su inicio. */
	static final int HORAS_HASTA_EL_FINAL = 2;

	/** La misma forma de hora que acepta la consulta del DAO (INICIO_PARTIDO). */
	private static final Pattern HORA_VALIDA = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");

	private CriterioResultadoPartido() {
	}

	/** Resultado puesto por cualquier vía; " - " o "-" cuentan como vacío. */
	static boolean tieneResultado(Integer golesFavor, String resultado) {

		if (golesFavor != null) {
			return true;
		}

		return resultado != null && !resultado.replace("-", "").isBlank();
	}

	static boolean tieneResultado(Partido partido) {
		return tieneResultado(partido.getGolesFavor(), partido.getResultado());
	}

	/** DIA a la HORA si es HH:mm válida (con trim); si no, a medianoche. */
	static LocalDateTime inicio(LocalDate dia, String hora) {

		String texto = hora == null ? "" : hora.trim();

		if (!HORA_VALIDA.matcher(texto).matches()) {
			return dia.atStartOfDay();
		}

		String[] partes = texto.split(":");

		return dia.atTime(LocalTime.of(Integer.parseInt(partes[0]), Integer.parseInt(partes[1])));
	}

	/** true si ya han pasado {@link #HORAS_HASTA_EL_FINAL} horas desde el inicio. */
	static boolean haTerminado(LocalDate dia, String hora, LocalDateTime ahora) {
		return !inicio(dia, hora).plusHours(HORAS_HASTA_EL_FINAL).isAfter(ahora);
	}
}
