package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mikedev.mutxamelcf.model.PartidoImagen;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Estado;

/**
 * Qué partidos entran en las imágenes para redes y cómo se reparten: lógica pura, sin base de datos ni dibujo.
 */
public final class SeleccionImagenesRedes {

    /** Más filas que estas y la modalidad se divide en varias imágenes. */
    public static final int MAX_FILAS = 8;

    public static final List<String> MODALIDADES = List.of("F11", "F8", "F5");

    private static final Pattern LETRA_FINAL = Pattern.compile("^(.*\\S)\\s+[\"“”'(]?([A-Za-z])[\"“”')]?$");

    private SeleccionImagenesRedes() {
    }

    /**
     * Los partidos de cada modalidad (F11, F8, F5; sin las que no tienen partidos) en el orden del club.
     *
     * <p>
     * Fuera: equipos de escuela ("Esc"), los excluidos por configuración, y en la PREVIA los partidos cancelados
     * (en RESULTADOS se quedan como "APLAZADO"). Un equipo sin modalidad (Primer Equipo, Filial, Veteranos) va a F11.
     */
    public static Map<String, List<PartidoImagen>> seleccionar(List<PartidoImagen> partidos, String tipo,
            Set<String> excluidos) {

        Set<String> fuera = excluidos == null ? Set.of() : excluidos;
        Map<String, List<PartidoImagen>> porModalidad = new LinkedHashMap<>();

        for (String modalidad : MODALIDADES) {
            porModalidad.put(modalidad, new ArrayList<>());
        }

        for (PartidoImagen original : partidos) {

            PartidoImagen p = conGoles(original);
            String nombre = p.equipo() == null ? "" : p.equipo().trim();

            if (nombre.contains("Esc") || fuera.contains(normalizar(nombre))) {
                continue;
            }

            if (ImagenesRedesModelos.PREVIA.equals(tipo) && p.cancelado()) {
                continue;
            }

            List<PartidoImagen> lista = porModalidad.get(modalidad(p));

            if (lista != null) {
                lista.add(p);
            }
        }

        porModalidad.values().forEach(lista -> lista.sort(ORDEN_CLUB));
        porModalidad.values().removeIf(List::isEmpty);

        return porModalidad;
    }

    private static final Pattern RESULTADO_TEXTO = Pattern.compile("^\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*$");

    /** Un partido sin goles pero con el resultado escrito como "3-1" (goles del Mutxamel - goles del rival). */
    private static PartidoImagen conGoles(PartidoImagen p) {

        if ((p.golesFavor() != null && p.golesContra() != null) || p.resultado() == null) {
            return p;
        }

        Matcher m = RESULTADO_TEXTO.matcher(p.resultado());

        if (!m.matches()) {
            return p;
        }

        return new PartidoImagen(p.partidoId(), p.equipoId(), p.equipo(), p.ordenEquipo(), p.modalidad(), p.rival(),
                p.clubRivalId(), p.dia(), p.hora(), p.campo(), p.local(), p.cancelado(),
                Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2)), p.resultado());
    }

    private static final Comparator<PartidoImagen> ORDEN_CLUB = Comparator
            .comparing((PartidoImagen p) -> numeroOrden(p.ordenEquipo()))
            .thenComparing(p -> p.equipo() == null ? "" : p.equipo())
            .thenComparing(PartidoImagen::dia)
            .thenComparing(p -> p.hora() == null ? "" : p.hora());

    private static String modalidad(PartidoImagen p) {

        String m = p.modalidad() == null ? "" : p.modalidad().trim().toUpperCase(Locale.ROOT);

        return m.isEmpty() ? "F11" : m;
    }

    private static int numeroOrden(String orden) {

        try {
            return Integer.parseInt(orden == null ? "" : orden.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** El nombre en minúsculas y sin espacios sobrantes, para comparar con la lista de exclusiones. */
    public static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Reparte las filas en las páginas necesarias (máximo {@link #MAX_FILAS} cada una) de forma equilibrada y sin
     * alterar el orden: 9 filas son 5 + 4, nunca 8 + 1.
     */
    public static <T> List<List<T>> paginar(List<T> filas) {

        List<List<T>> paginas = new ArrayList<>();

        if (filas.isEmpty()) {
            return paginas;
        }

        int total = (filas.size() + MAX_FILAS - 1) / MAX_FILAS;
        int base = filas.size() / total;
        int resto = filas.size() % total;
        int desde = 0;

        for (int i = 0; i < total; i++) {
            int tamano = base + (i < resto ? 1 : 0);
            paginas.add(new ArrayList<>(filas.subList(desde, desde + tamano)));
            desde += tamano;
        }

        return paginas;
    }

    // ---- textos de las filas ------------------------------------------------------------------

    /** "VS CLUB “B”": el rival en mayúsculas y con la letra del equipo final entre comillas. */
    public static String lineaRival(String rival) {

        String texto = rival == null ? "" : rival.trim().replaceAll("\\s+", " ");
        Matcher m = LETRA_FINAL.matcher(texto);

        if (m.matches()) {
            texto = m.group(1) + " “" + m.group(2).toUpperCase(Locale.ROOT) + "”";
        }

        return ("VS " + texto).toUpperCase(Locale.ROOT).trim();
    }

    /** "19:15 H" (o "-- H" si no hay hora). */
    public static String hora(String hora) {

        String h = hora == null ? "" : hora.trim();

        return h.isEmpty() ? "-- H" : h + " H";
    }

    /** El marcador con el local primero ("3 - 1"), o null si el partido no tiene goles. */
    public static String marcador(PartidoImagen p) {

        if (p.golesFavor() == null || p.golesContra() == null) {
            return null;
        }

        return p.local() ? p.golesFavor() + " - " + p.golesContra() : p.golesContra() + " - " + p.golesFavor();
    }

    public static Estado estado(PartidoImagen p) {

        if (p.cancelado()) {
            return Estado.APLAZADO;
        }

        if (p.golesFavor() == null || p.golesContra() == null) {
            return Estado.SIN_RESULTADO;
        }

        return p.golesFavor() > p.golesContra() ? Estado.VICTORIA
                : p.golesFavor() < p.golesContra() ? Estado.DERROTA : Estado.EMPATE;
    }
}
