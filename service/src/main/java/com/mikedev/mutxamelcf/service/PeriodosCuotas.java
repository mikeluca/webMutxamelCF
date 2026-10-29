package com.mikedev.mutxamelcf.service;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los periodos de las cuotas de una temporada. Lo usan "Asignar cuotas" del panel y la asignación automática del
 * reparto, para que las dos calculen lo mismo.
 */
public final class PeriodosCuotas {

	private static final Pattern NOMBRE_TEMPORADA = Pattern.compile("(\\d{4})\\s*[/\\-]\\s*(\\d{4})");

	private PeriodosCuotas() {
	}

	/** Un concepto es una cuota mensual si su nombre contiene "cuota"; el resto (matrícula, equipación…) es único. */
	public static boolean esCuotaMensual(String nombreConcepto) {
		return nombreConcepto != null && nombreConcepto.trim().toUpperCase(Locale.ROOT).contains("CUOTA");
	}

	/**
	 * Primer y último mes ("yyyy-MM") de la temporada: de septiembre a junio, con los años del nombre
	 * ("2026/2027") o, si no los trae, el de su fecha de inicio (o el actual).
	 */
	public static String[] periodosTemporada(String nombreTemporada, Date fechaInicio) {
		if (nombreTemporada != null) {
			Matcher matcher = NOMBRE_TEMPORADA.matcher(nombreTemporada);

			if (matcher.find()) {
				return new String[] { matcher.group(1) + "-09", matcher.group(2) + "-06" };
			}
		}

		int inicio = fechaInicio != null ? anio(fechaInicio) : YearMonth.now().getYear();

		return new String[] { inicio + "-09", (inicio + 1) + "-06" };
	}

	private static int anio(Date fecha) {
		return fecha instanceof java.sql.Date sql ? sql.toLocalDate().getYear()
				: fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).getYear();
	}

	/** Los meses de [periodoInicio] a [periodoFin], ambos incluidos; vacío si no son válidos (o hay más de 24 meses). */
	public static List<String> generar(String periodoInicio, String periodoFin) {
		if (periodoInicio == null || periodoFin == null || periodoInicio.isBlank() || periodoFin.isBlank()) {
			return List.of();
		}

		try {
			YearMonth inicio = YearMonth.parse(periodoInicio);
			YearMonth fin = YearMonth.parse(periodoFin);

			if (inicio.isAfter(fin) || inicio.plusMonths(24).isBefore(fin)) {
				return List.of();
			}

			List<String> periodos = new ArrayList<>();
			for (YearMonth periodo = inicio; !periodo.isAfter(fin); periodo = periodo.plusMonths(1)) {
				periodos.add(periodo.toString());
			}

			return periodos;
		} catch (DateTimeParseException exception) {
			return List.of();
		}
	}

	/**
	 * Los meses que se cobran a quien recibe su equipo definitivo en [mesAsignacion]: toda la temporada si se asigna
	 * en septiembre o antes (pretemporada) y, si se asigna más tarde, desde el mes de la asignación.
	 */
	public static List<String> generarDesde(String periodoInicio, String periodoFin, YearMonth mesAsignacion) {
		List<String> todos = generar(periodoInicio, periodoFin);

		if (mesAsignacion == null) {
			return todos;
		}

		return todos.stream().filter(p -> !YearMonth.parse(p).isBefore(mesAsignacion)).toList();
	}
}
