package com.mikedev.mutxamelcf.serviceimpl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.mikedev.mutxamelcf.model.FilaClasificacionVeteranos;

/**
 * Lee la página https://ligaveteranosalicante.es/scoretable/ guardada desde el navegador ("Guardar como… → Página web").
 * La sube siempre una persona: este código no descarga nunca la página.
 *
 * <p>
 * Estructura de la página: las pestañas {@code ul.nav-tabs} llevan, cada una, un {@code <a href="…#id">} con el nombre de
 * la competición ("Liga Veteranos Ciudad de Alicante", "Copa grupo A"...); cada pestaña tiene su {@code div.tab-pane} con
 * ese id y una {@code table.scoretable-tbl}. La primera fila ({@code tr.scoretable-head}) es la cabecera y las demás
 * llevan POS, EQUIPO (en {@code span.scoretable-teamname}), PJ, PG, PE, PP, GF, GC, DIF y PTS.
 */
final class ClasificacionLvcaParser {

    static final String NO_ES_LA_PAGINA = "No parece la clasificación de la Liga Veteranos Ciudad de Alicante";

    /** Una competición leída de la página. */
    record Competicion(String nombre, List<FilaClasificacionVeteranos> filas) {
    }

    private ClasificacionLvcaParser() {
    }

    /**
     * Todas las competiciones de la página en las que aparece el Mutxamel, en el orden de las pestañas. Las demás tablas
     * se ignoran (ni siquiera se validan).
     *
     * @throws IllegalArgumentException si el archivo no es esa página, o si una tabla del Mutxamel está incompleta
     *                                  (falta un número o hay menos de 2 equipos)
     */
    static List<Competicion> parsear(byte[] archivo) {

        if (archivo == null || archivo.length == 0) {
            throw new IllegalArgumentException(NO_ES_LA_PAGINA);
        }

        Document pagina = Jsoup.parse(new String(archivo, StandardCharsets.UTF_8));
        Elements pestanas = pagina.select("ul.nav-tabs a[href]");

        if (pestanas.isEmpty() || pagina.select("table.scoretable-tbl").isEmpty()) {
            throw new IllegalArgumentException(NO_ES_LA_PAGINA);
        }

        List<Competicion> encontradas = new ArrayList<>();
        int tablasLeidas = 0;

        for (Element pestana : pestanas) {

            String href = pestana.attr("href");
            int almohadilla = href.lastIndexOf('#');
            String nombre = espacios(pestana.text());

            if (almohadilla < 0 || almohadilla == href.length() - 1 || nombre.isEmpty()) {
                continue;
            }

            Element panel = pagina.getElementById(href.substring(almohadilla + 1));
            Element tabla = panel == null ? null : panel.selectFirst("table.scoretable-tbl");

            if (tabla == null) {
                continue;
            }

            tablasLeidas++;

            // Solo se lee en serio la tabla en la que aparece el Mutxamel: antes de validar nada, se mira si está.
            if (tabla.select("span.scoretable-teamname").stream().noneMatch(s -> esMutxamel(s.text()))) {
                continue;
            }

            encontradas.add(new Competicion(nombre, leerTabla(nombre, tabla)));
        }

        if (tablasLeidas == 0) {
            throw new IllegalArgumentException(NO_ES_LA_PAGINA);
        }

        return encontradas;
    }

    /** El nombre del equipo, sin acentos, puntos ni espacios de más y en mayúsculas, empieza por MUTXAMEL. */
    static boolean esMutxamel(String equipo) {
        return NombreClub.normalizar(equipo).startsWith("MUTXAMEL");
    }

    private static List<FilaClasificacionVeteranos> leerTabla(String competicion, Element tabla) {

        List<FilaClasificacionVeteranos> filas = new ArrayList<>();
        int numeroFila = 0;

        for (Element tr : tabla.select("tr")) {

            if (tr.hasClass("scoretable-head") || tr.select("th").size() > 0 && tr.select("td").isEmpty()) {
                continue;
            }

            Elements celdas = tr.children().select("td");

            if (celdas.isEmpty()) {
                continue;
            }

            numeroFila++;

            Element nombre = tr.selectFirst("span.scoretable-teamname");

            if (celdas.size() < 10 || nombre == null || espacios(nombre.text()).isEmpty()) {
                throw incompleta(competicion, numeroFila, "faltan columnas");
            }

            String equipo = espacios(nombre.text());

            filas.add(new FilaClasificacionVeteranos(
                    numero(celdas.get(0), competicion, numeroFila, "POS"),
                    equipo,
                    numero(celdas.get(2), competicion, numeroFila, "PJ"),
                    numero(celdas.get(3), competicion, numeroFila, "PG"),
                    numero(celdas.get(4), competicion, numeroFila, "PE"),
                    numero(celdas.get(5), competicion, numeroFila, "PP"),
                    numero(celdas.get(6), competicion, numeroFila, "GF"),
                    numero(celdas.get(7), competicion, numeroFila, "GC"),
                    numero(celdas.get(8), competicion, numeroFila, "DIF"),
                    numero(celdas.get(9), competicion, numeroFila, "PTS"),
                    esMutxamel(equipo)));
        }

        if (filas.size() < 2) {
            throw new IllegalArgumentException(NO_ES_LA_PAGINA + ": la tabla de «" + competicion
                    + "» tiene menos de 2 equipos");
        }

        return filas;
    }

    private static int numero(Element celda, String competicion, int fila, String columna) {

        // El menos de la diferencia de goles puede venir como guion tipográfico.
        String texto = celda.text().replace('−', '-').replace('–', '-').replaceAll("\\s+", "").trim();

        try {
            return Integer.parseInt(texto.startsWith("+") ? texto.substring(1) : texto);
        } catch (NumberFormatException e) {
            throw incompleta(competicion, fila, "falta o no es un número el dato " + columna);
        }
    }

    private static IllegalArgumentException incompleta(String competicion, int fila, String motivo) {
        return new IllegalArgumentException(
                NO_ES_LA_PAGINA + ": la tabla de «" + competicion + "» está incompleta (fila " + fila + ": " + motivo + ")");
    }

    private static String espacios(String texto) {
        return texto == null ? "" : texto.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}
