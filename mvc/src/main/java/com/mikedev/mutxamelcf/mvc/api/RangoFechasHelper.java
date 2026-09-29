package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * DB-04: {@code /api/app/calendario} y
 * {@code /api/app/sesiones-entrenamiento} aceptaban cualquier rango de
 * fechas (p. ej. desde=1900-01-01&amp;hasta=2100-01-01), lo que fuerza a
 * la BD a recorrer todo el histórico. Se acota a un margen amplio, muy
 * por debajo de ese rango absurdo pero por encima de cualquier vista real
 * de la app.
 *
 * N-02: el límite estaba en 93 días (un trimestre), pero
 * calendario_page.dart pide desde el inicio de la temporada activa hasta
 * hoy + 2 meses; con una temporada de 10 meses (o sin temporada activa,
 * que amplía aún más el rango) esa petición supera 93 días y el
 * calendario de familias/coordinadores se queda vacío. Se sube a 400 días
 * (una temporada completa de sobra) para cubrir ese caso real y seguir
 * bloqueando el rango 1900-2100 que motivó el límite.
 */
final class RangoFechasHelper {

    private static final long DIAS_MAXIMOS_RANGO = 400;

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
