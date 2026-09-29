package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * DB-04: {@code /api/app/calendario} y
 * {@code /api/app/sesiones-entrenamiento} aceptaban cualquier rango de
 * fechas (p. ej. desde=1900-01-01&amp;hasta=2100-01-01), lo que fuerza a
 * la BD a recorrer todo el histórico. Se acota a 93 días (un trimestre),
 * más que de sobra para cualquier vista de calendario real de la app.
 */
final class RangoFechasHelper {

    private static final long DIAS_MAXIMOS_RANGO = 93;

    private RangoFechasHelper() {
    }

    static void validar(LocalDate desde, LocalDate hasta) {

        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas 'desde' y 'hasta' son obligatorias");
        }

        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }

        if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMOS_RANGO) {
            throw new IllegalArgumentException(
                    "El rango de fechas no puede superar " + DIAS_MAXIMOS_RANGO + " días");
        }
    }
}
