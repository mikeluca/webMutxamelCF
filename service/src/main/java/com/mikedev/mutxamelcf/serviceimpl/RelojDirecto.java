package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDateTime;

import com.mikedev.mutxamelcf.model.RelojPartido;

/**
 * Cálculo del reloj en directo a partir de la fase y del inicio de la parte en juego, que salen de los eventos no
 * anulados que cada servicio ya lee. Sin acceso a datos.
 *
 * <p>Zona horaria: FECHA de PARTIDO_LIVE_EVENTOS es una hora local sin zona (LocalDateTime) que se escribe con
 * {@code LocalDateTime.now(clock)}, y el reloj de la aplicación es Europe/Madrid (AppServiceConfig). Por eso se
 * convierte a epoch ms con la zona del mismo {@link Clock}: la misma con la que se generó.
 */
final class RelojDirecto {

    static final String PRIMERA_PARTE = "PRIMERA_PARTE";
    static final String DESCANSO = "DESCANSO";
    static final String SEGUNDA_PARTE = "SEGUNDA_PARTE";

    private RelojDirecto() {
    }

    /** Duración de una parte según la modalidad (F11 por defecto, también sin modalidad). */
    static int minutosParte(String modalidad, int f11, int f8, int f5) {

        if ("F8".equals(modalidad)) {
            return f8;
        }

        return "F5".equals(modalidad) ? f5 : f11;
    }

    /** true si la fase tiene reloj: primera parte, descanso o segunda parte. */
    static boolean conReloj(String fase) {
        return PRIMERA_PARTE.equals(fase) || DESCANSO.equals(fase) || SEGUNDA_PARTE.equals(fase);
    }

    /**
     * @param fase la fase del partido; sin reloj (null, SIN_EMPEZAR, FINAL, FINALIZADO) devuelve null
     * @param inicioParte la fecha del último INICIO (1.ª parte) o SEGUNDA_PARTE (2.ª) no anulado
     */
    static RelojPartido reloj(String fase, LocalDateTime inicioParte, int minutosParte, Clock clock) {

        if (!conReloj(fase)) {
            return null;
        }

        boolean descanso = DESCANSO.equals(fase);

        if (!descanso && inicioParte == null) {
            return null;
        }

        Long inicio = descanso ? null : inicioParte.atZone(clock.getZone()).toInstant().toEpochMilli();

        return new RelojPartido(fase, inicio, PRIMERA_PARTE.equals(fase) ? 0 : minutosParte, minutosParte,
                clock.millis());
    }
}
