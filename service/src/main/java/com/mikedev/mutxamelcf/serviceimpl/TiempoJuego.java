package com.mikedev.mutxamelcf.serviceimpl;

/** El tiempo de una partida de Parejas tal como se enseña en el panel: {@code m:ss,d} (minutos, segundos y décimas). */
public final class TiempoJuego {

    private TiempoJuego() {
    }

    /** 83450 → "1:23,4" (las décimas se truncan); 0 o menos → "—" (sin marca). */
    public static String formatear(int ms) {

        if (ms <= 0) {
            return "—";
        }

        int decimas = ms / 100;

        return (decimas / 600) + ":" + String.format("%02d", (decimas / 10) % 60) + "," + (decimas % 10);
    }

    /** Para Thymeleaf: {@code ${tiempo.formatear(ms)}}. */
    public static final TiempoJuego INSTANCIA = new TiempoJuego();

    /** Igual que {@link #formatear(int)}, accesible desde una plantilla. */
    public String f(int ms) {
        return formatear(ms);
    }
}
