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
 * hoy + 2 meses; con una temporada de 10 meses esa petición ya superaba
 * los 93 días.
 *
 * Bug post-N-02: subir el límite a 400 no bastaba para el caso SIN
 * temporada activa configurada, donde el cliente cae a un respaldo de
 * "hoy - 1 año" como 'desde' (ver calendario_page.dart): 1 año + 2 meses
 * son hasta ~428 días, por encima de 400, así que esa petición seguía
 * devolviendo 400 Bad Request -- y como el cliente ignora ese error en
 * silencio por equipo, el calendario de familiares y del entrenador en
 * "Área Club" se veía completamente vacío sin ningún aviso. Se sube a
 * 450 para cubrir con margen ese respaldo de 1 año + 2 meses y seguir
 * bloqueando el rango 1900-2100 que motivó el límite.
 */
final class RangoFechasHelper {

    private static final long DIAS_MAXIMOS_RANGO = 450;

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
