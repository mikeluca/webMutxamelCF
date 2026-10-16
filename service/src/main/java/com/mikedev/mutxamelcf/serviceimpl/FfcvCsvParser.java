package com.mikedev.mutxamelcf.serviceimpl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mikedev.mutxamelcf.model.FfcvErrorLinea;
import com.mikedev.mutxamelcf.model.FfcvFilaCsv;
import com.mikedev.mutxamelcf.model.FfcvParseResult;

/**
 * Lee el "LstPartidos.csv" que el coordinador descarga de la intranet de la
 * FFCV. Es un servicio puro (sin base de datos ni red): solo transforma el
 * contenido del archivo subido.
 *
 * Estructura por bloques:
 * <pre>
 *   17-10-2026, (Sábado)                                     línea de fecha
 *   Segona FFCV, Grup - 7;Campo;Dirección Campo;Hora;...      cabecera de competición
 *   Local  -  Visitante;Campo;Dirección;Hora ;Jornada;...     partidos
 * </pre>
 *
 * Privacidad: el árbitro del CSV no se lee, no se guarda ni se registra, y
 * los mensajes de error nunca incluyen el texto de la línea.
 */
public final class FfcvCsvParser {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final Pattern LINEA_FECHA = Pattern.compile("^\\s*(\\d{1,2})-(\\d{1,2})-(\\d{4})\\s*,.*$");

    private static final Pattern SEPARADOR_EQUIPOS = Pattern.compile("^(.*?)\\s{2,}-\\s{2,}(.*)$");

    private static final Pattern LADO_CLUB = Pattern.compile("^Mutxamel C\\.F\\.(\\s+\"([A-Z])\")?$");

    private static final Pattern HORA = Pattern.compile("^\\d{1,2}:\\d{2}$");

    private static final int LONGITUD_MAXIMA_TEXTO = 150;

    private FfcvCsvParser() {
    }

    public static FfcvParseResult parsear(byte[] archivo) {
        return parsear(decodificar(archivo));
    }

    /**
     * windows-1252 salvo que el archivo traiga BOM UTF-8, en cuyo caso se
     * lee como UTF-8 (y se descarta el BOM).
     */
    static String decodificar(byte[] archivo) {

        if (archivo == null) {
            return "";
        }

        boolean bomUtf8 = archivo.length >= 3
                && (archivo[0] & 0xFF) == 0xEF
                && (archivo[1] & 0xFF) == 0xBB
                && (archivo[2] & 0xFF) == 0xBF;

        if (bomUtf8) {
            return new String(archivo, 3, archivo.length - 3, StandardCharsets.UTF_8);
        }

        return new String(archivo, WINDOWS_1252);
    }

    public static FfcvParseResult parsear(String contenido) {

        List<FfcvFilaCsv> filas = new ArrayList<>();
        List<FfcvErrorLinea> errores = new ArrayList<>();

        LocalDate fechaActual = null;
        String competicionActual = null;

        String[] lineas = contenido == null ? new String[0] : contenido.split("\\r\\n|\\r|\\n", -1);

        for (int i = 0; i < lineas.length; i++) {

            int numeroLinea = i + 1;
            String linea = lineas[i];

            if (linea.isBlank() || esTituloDelListado(linea)) {
                continue;
            }

            Matcher fecha = LINEA_FECHA.matcher(linea);

            if (fecha.matches()) {
                try {
                    fechaActual = LocalDate.of(
                            Integer.parseInt(fecha.group(3)),
                            Integer.parseInt(fecha.group(2)),
                            Integer.parseInt(fecha.group(1)));
                } catch (DateTimeException e) {
                    fechaActual = null;
                    errores.add(new FfcvErrorLinea(numeroLinea, "Fecha no válida"));
                }
                competicionActual = null;
                continue;
            }

            String[] campos = linea.split(";", -1);

            if (esCabeceraDeCompeticion(campos)) {
                competicionActual = normalizarCompeticion(campos[0]);
                continue;
            }

            if (fechaActual == null || competicionActual == null) {
                errores.add(new FfcvErrorLinea(numeroLinea, "Partido fuera de un bloque de fecha y competición"));
                continue;
            }

            procesarPartido(campos, numeroLinea, fechaActual, competicionActual, filas, errores);
        }

        return new FfcvParseResult(filas, errores);
    }

    private static void procesarPartido(
            String[] campos,
            int numeroLinea,
            LocalDate fecha,
            String competicion,
            List<FfcvFilaCsv> filas,
            List<FfcvErrorLinea> errores) {

        // Local - Visitante; Campo; Dirección; Hora; Jornada; (Árbitro; Resul.; Obs.)
        if (campos.length < 5) {
            errores.add(new FfcvErrorLinea(numeroLinea, "La fila no tiene todas las columnas"));
            return;
        }

        Matcher equipos = SEPARADOR_EQUIPOS.matcher(campos[0].trim());

        if (!equipos.matches()) {
            errores.add(new FfcvErrorLinea(numeroLinea, "No se distingue el equipo local del visitante"));
            return;
        }

        String ladoLocal = equipos.group(1).trim();
        String ladoVisitante = equipos.group(2).trim();

        Matcher clubLocal = LADO_CLUB.matcher(ladoLocal);
        Matcher clubVisitante = LADO_CLUB.matcher(ladoVisitante);

        boolean mutxamelLocal = clubLocal.matches();
        boolean mutxamelVisitante = clubVisitante.matches();

        if (!mutxamelLocal && !mutxamelVisitante) {
            errores.add(new FfcvErrorLinea(numeroLinea, "Ningún equipo de la fila es el Mutxamel C.F."));
            return;
        }

        String campo = limitar(campos[1].trim());
        String hora = campos[3].trim();
        String textoJornada = campos[4].trim();

        if (!hora.isEmpty() && !HORA.matcher(hora).matches()) {
            errores.add(new FfcvErrorLinea(numeroLinea, "Hora no válida (se esperaba HH:mm)"));
            return;
        }

        Integer jornada = null;

        if (!textoJornada.isEmpty()) {
            try {
                jornada = Integer.valueOf(textoJornada);
            } catch (NumberFormatException e) {
                errores.add(new FfcvErrorLinea(numeroLinea, "Jornada no válida"));
                return;
            }
        }

        String horaNormalizada = hora.isEmpty() ? null : hora;
        String campoNormalizado = campo.isEmpty() ? null : campo;

        /*
         * Si los dos lados son del Mutxamel (A contra B en el mismo grupo)
         * se generan dos filas, una por equipo: cada una con su letra y su
         * propio lado local/visitante.
         */
        if (mutxamelLocal) {
            filas.add(new FfcvFilaCsv(fecha, competicion, clubLocal.group(2), true,
                    limitar(ladoVisitante), campoNormalizado, horaNormalizada, jornada));
        }

        if (mutxamelVisitante) {
            filas.add(new FfcvFilaCsv(fecha, competicion, clubVisitante.group(2), false,
                    limitar(ladoLocal), campoNormalizado, horaNormalizada, jornada));
        }
    }

    private static boolean esTituloDelListado(String linea) {
        return linea.trim().equalsIgnoreCase("Listado de Partidos");
    }

    private static boolean esCabeceraDeCompeticion(String[] campos) {
        return campos.length >= 2 && campos[1].trim().equalsIgnoreCase("Campo");
    }

    /**
     * trim, espacios (y tabuladores) colapsados, sin espacios antes de las
     * comas y "Grup - 7" -> "Grup 7". Es la misma forma en que se guarda
     * EQUIPOS_FFCV.COMPETICION, para que coincidan al buscar.
     */
    public static String normalizarCompeticion(String competicion) {

        if (competicion == null) {
            return "";
        }

        return competicion
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("\\s+,", ",")
                .replaceAll("Grup\\s*-\\s*(\\d+)", "Grup $1");
    }

    private static String limitar(String texto) {
        return texto.length() > LONGITUD_MAXIMA_TEXTO ? texto.substring(0, LONGITUD_MAXIMA_TEXTO) : texto;
    }

}
