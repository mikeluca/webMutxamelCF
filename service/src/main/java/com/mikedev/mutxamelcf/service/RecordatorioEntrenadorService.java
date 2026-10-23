package com.mikedev.mutxamelcf.service;

import java.time.LocalDateTime;

/**
 * "Recordar al entrenador" desde el panel de fin de semana de coordinación: un aviso a los
 * entrenadores con cuenta del equipo para que publiquen la convocatoria o metan el resultado de
 * un partido. Solo COORDINADOR y ADMIN_APP ({@link SecurityException} al resto).
 */
public interface RecordatorioEntrenadorService {

    String TIPO_CONVOCATORIA = "CONVOCATORIA";
    String TIPO_RESULTADO = "RESULTADO";

    /** Valores de data["tipo"] de los push. */
    String PUSH_CONVOCATORIA_PENDIENTE = "CONVOCATORIA_PENDIENTE";
    String PUSH_RESULTADO_PENDIENTE = "RESULTADO_PENDIENTE";

    /**
     * Envía el recordatorio. {@link IllegalArgumentException} si el partido o el tipo no son válidos
     * o el recordatorio no procede (ya tiene convocatoria, ya tiene resultado, está cancelado...);
     * {@link SinEntrenadores} si el equipo no tiene entrenadores con cuenta;
     * {@link DemasiadoPronto} si ya se recordó lo mismo hace menos de 2 horas.
     */
    Enviado enviar(Long usuarioAppId, Long partidoId, String tipo);

    /** destinatarios: a cuántos entrenadores se avisó; fecha: cuándo. */
    record Enviado(int destinatarios, LocalDateTime fecha) {
    }

    /** El equipo no tiene entrenadores con cuenta en la app (409). */
    class SinEntrenadores extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public SinEntrenadores() {
            super("El equipo no tiene entrenadores con cuenta en la app");
        }

        public SinEntrenadores(String mensaje) {
            super(mensaje);
        }
    }

    /** Ya se envió este recordatorio hace menos de 2 horas (429); [ultimo] es cuándo. */
    class DemasiadoPronto extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient LocalDateTime ultimo;

        public DemasiadoPronto(LocalDateTime ultimo) {
            super("Ya se recordó esto hace poco");
            this.ultimo = ultimo;
        }

        public LocalDateTime ultimo() {
            return ultimo;
        }
    }
}
