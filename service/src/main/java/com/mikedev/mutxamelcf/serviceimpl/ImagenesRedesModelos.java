package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;

/** Los datos que recibe el renderizador de las imágenes para redes sociales. */
public final class ImagenesRedesModelos {

    private ImagenesRedesModelos() {
    }

    public static final String PREVIA = "PREVIA";
    public static final String RESULTADOS = "RESULTADOS";

    /** Cómo acabó (o no) un partido, para la etiqueta pequeña bajo el marcador. */
    public enum Estado {
        VICTORIA, EMPATE, DERROTA, SIN_RESULTADO, APLAZADO
    }

    /**
     * Una fila de la imagen. {@code equipo} y {@code rival} ya van en mayúsculas y con la letra entre comillas;
     * {@code marcador} ("3 - 1", local primero) solo en RESULTADOS; {@code escudo} es el PNG del rival o null.
     */
    public record Fila(
            String equipo,
            String rival,
            String hora,
            String fecha,
            String campo,
            String marcador,
            Estado estado,
            byte[] escudo) {
    }

    /** Una imagen completa: tipo (PREVIA/RESULTADOS), modalidad (F11/F8/F5), página y sus filas. */
    public record Imagen(String tipo, String modalidad, int pagina, int totalPaginas, List<Fila> filas) {
    }
}
