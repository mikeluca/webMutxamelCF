package com.mikedev.mutxamelcf.serviceimpl;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.mikedev.mutxamelcf.model.FfcvClasificacionesParseResult;
import com.mikedev.mutxamelcf.model.FfcvErrorLinea;
import com.mikedev.mutxamelcf.model.FfcvFilaClasificacion;

/**
 * Lee el "ListadoClasificaciones.xlsx" que el coordinador descarga de la
 * intranet de la FFCV (todas las clasificaciones de la Comunitat). Es un
 * servicio puro, sin base de datos ni red.
 *
 * La cabecera se busca por su texto (no por número de fila) y las columnas por
 * su nombre (no por posición). Los textos del Excel traen basura (espacios
 * duros, tabuladores, espacios dobles) que se limpia aquí. No se filtra nada
 * por club: eso lo decide el servicio con el código de club.
 */
public final class FfcvClasificacionesParser {

    /** Dónde se busca la cabecera: la fila 1 es un título y la 2 la cabecera. */
    private static final int FILAS_BUSCANDO_CABECERA = 30;

    private static final int LONGITUD_MAXIMA_NOMBRE = 150;

    private static final Pattern ENTERO = Pattern.compile("^-?\\d+$");
    private static final Pattern ENTERO_CON_DECIMALES_VACIOS = Pattern.compile("^(-?\\d+)[.,]0+$");

    private static final DateTimeFormatter FECHA_GUIONES = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final DateTimeFormatter FECHA_BARRAS = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /** Columnas necesarias, con el nombre tal como se muestra en los errores. */
    private enum Columna {
        COMPETICION("Competición"),
        GRUPO("Grupo"),
        EQUIPO("Equipo"),
        LETRA("Letra"),
        CLUB("Club"),
        JORNADA("Jornada"),
        POSICION("Posición"),
        PUNTOS("Puntos"),
        PJ("Partidos jugados"),
        PG("Partidos ganados"),
        PE("Partidos empatados"),
        PP("Partidos perdidos"),
        GF("Goles a favor"),
        GC("Goles en contra"),
        ULT_PARTIDO("Ult. Partido Jugado");

        private final String nombre;

        Columna(String nombre) {
            this.nombre = nombre;
        }
    }

    private FfcvClasificacionesParser() {
    }

    /**
     * @throws IllegalArgumentException si el archivo no es un .xlsx legible, no
     *                                  se encuentra la cabecera o falta alguna columna
     */
    public static FfcvClasificacionesParseResult parsear(byte[] archivo) {

        if (archivo == null || archivo.length == 0) {
            throw new IllegalArgumentException("El archivo está vacío");
        }

        try (Workbook libro = abrir(archivo)) {

            if (libro.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("El Excel no tiene ninguna hoja");
            }

            return parsearHoja(libro.getSheetAt(0));

        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel");
        }
    }

    private static Workbook abrir(byte[] archivo) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(archivo));
        } catch (Exception e) {
            throw new IllegalArgumentException("El archivo no es un Excel .xlsx válido");
        }
    }

    private static FfcvClasificacionesParseResult parsearHoja(Sheet hoja) {

        DataFormatter formateador = new DataFormatter(Locale.ROOT);

        int filaCabecera = -1;
        Map<String, Integer> columnasPorNombre = null;

        int ultima = Math.min(hoja.getLastRowNum(), FILAS_BUSCANDO_CABECERA);

        for (int i = hoja.getFirstRowNum(); i <= ultima && filaCabecera < 0; i++) {

            Row fila = hoja.getRow(i);

            if (fila == null) {
                continue;
            }

            Map<String, Integer> candidatas = nombresDeColumnas(fila, formateador);

            if (candidatas.containsKey(plegar("Competición")) && candidatas.containsKey(plegar("Posición"))) {
                filaCabecera = i;
                columnasPorNombre = candidatas;
            }
        }

        if (columnasPorNombre == null) {
            throw new IllegalArgumentException(
                    "No se encuentra la fila de cabecera (Competición, Grupo, Equipo, Posición...). "
                            + "¿Es el ListadoClasificaciones.xlsx de la FFCV?");
        }

        Map<Columna, Integer> columnas = new EnumMap<>(Columna.class);
        List<String> faltan = new ArrayList<>();

        for (Columna columna : Columna.values()) {
            Integer indice = columnasPorNombre.get(plegar(columna.nombre));
            if (indice == null) {
                faltan.add(columna.nombre);
            } else {
                columnas.put(columna, indice);
            }
        }

        if (!faltan.isEmpty()) {
            throw new IllegalArgumentException("Faltan columnas en el archivo: " + String.join(", ", faltan));
        }

        List<FfcvFilaClasificacion> filas = new ArrayList<>();
        List<FfcvErrorLinea> errores = new ArrayList<>();
        Set<String> gruposConErrores = new HashSet<>();

        for (int i = filaCabecera + 1; i <= hoja.getLastRowNum(); i++) {

            Row fila = hoja.getRow(i);

            if (fila == null) {
                continue;
            }

            String competicion = texto(fila, columnas.get(Columna.COMPETICION), formateador);
            String grupo = texto(fila, columnas.get(Columna.GRUPO), formateador);
            String equipo = texto(fila, columnas.get(Columna.EQUIPO), formateador);

            if (competicion.isEmpty() && equipo.isEmpty()) {
                continue;
            }

            String clave = clave(competicion, grupo);

            try {
                filas.add(leerFila(fila, i + 1, clave, equipo, columnas, formateador));
            } catch (IllegalArgumentException e) {
                errores.add(new FfcvErrorLinea(i + 1, e.getMessage()));
                gruposConErrores.add(clave);
            }
        }

        return new FfcvClasificacionesParseResult(filas, errores, gruposConErrores);
    }

    private static FfcvFilaClasificacion leerFila(
            Row fila,
            int filaExcel,
            String clave,
            String equipo,
            Map<Columna, Integer> columnas,
            DataFormatter formateador) {

        if (equipo.isEmpty()) {
            throw new IllegalArgumentException("Fila sin nombre de equipo");
        }

        return new FfcvFilaClasificacion(
                filaExcel,
                clave,
                letra(texto(fila, columnas.get(Columna.LETRA), formateador)),
                club(fila.getCell(columnas.get(Columna.CLUB)), formateador),
                equipo.length() > LONGITUD_MAXIMA_NOMBRE ? equipo.substring(0, LONGITUD_MAXIMA_NOMBRE) : equipo,
                entero(fila.getCell(columnas.get(Columna.JORNADA)), formateador, Columna.JORNADA, false),
                entero(fila.getCell(columnas.get(Columna.POSICION)), formateador, Columna.POSICION, true),
                entero(fila.getCell(columnas.get(Columna.PUNTOS)), formateador, Columna.PUNTOS, true),
                entero(fila.getCell(columnas.get(Columna.PJ)), formateador, Columna.PJ, true),
                entero(fila.getCell(columnas.get(Columna.PG)), formateador, Columna.PG, true),
                entero(fila.getCell(columnas.get(Columna.PE)), formateador, Columna.PE, true),
                entero(fila.getCell(columnas.get(Columna.PP)), formateador, Columna.PP, true),
                entero(fila.getCell(columnas.get(Columna.GF)), formateador, Columna.GF, true),
                entero(fila.getCell(columnas.get(Columna.GC)), formateador, Columna.GC, true),
                fecha(fila.getCell(columnas.get(Columna.ULT_PARTIDO)), formateador));
    }

    /** Misma clave que EQUIPOS_FFCV.COMPETICION: normalizarCompeticion(competición + ", " + grupo). */
    static String clave(String competicion, String grupo) {
        String completa = grupo == null || grupo.isEmpty() ? competicion : competicion + ", " + grupo;
        return FfcvCsvParser.normalizarCompeticion(completa);
    }

    private static Map<String, Integer> nombresDeColumnas(Row fila, DataFormatter formateador) {

        Map<String, Integer> nombres = new HashMap<>();

        for (Cell celda : fila) {
            String nombre = plegar(formateador.formatCellValue(celda));
            if (!nombre.isEmpty()) {
                nombres.putIfAbsent(nombre, celda.getColumnIndex());
            }
        }

        return nombres;
    }

    /** Minúsculas, sin acentos y sin espacios (duros, tabuladores, dobles) sobrantes. */
    private static String plegar(String texto) {
        String sinAcentos = Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }

    /** Espacios duros y otros espacios Unicode a espacio normal, colapsados y con trim. */
    static String limpiar(String texto) {

        if (texto == null) {
            return "";
        }

        return texto
                .replaceAll("[\\u00A0\\u2007\\u202F\\u200B\\uFEFF]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String texto(Row fila, int columna, DataFormatter formateador) {
        return limpiar(formateador.formatCellValue(fila.getCell(columna)));
    }

    private static String letra(String texto) {

        if (texto.isEmpty() || texto.equals("-")) {
            return null;
        }

        if (texto.length() > 2) {
            throw new IllegalArgumentException("Letra no válida");
        }

        return texto.toUpperCase(Locale.ROOT);
    }

    /** El club nunca es motivo de error: si no se lee, la fila simplemente no es del Mutxamel. */
    private static Integer club(Cell celda, DataFormatter formateador) {
        try {
            return entero(celda, formateador, Columna.CLUB, false);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Entero venga como número o como texto. Los numéricos se leen sin pasar
     * por el formato de la celda (que podría añadir separadores de miles).
     */
    private static Integer entero(Cell celda, DataFormatter formateador, Columna columna, boolean obligatorio) {

        if (celda != null && tipoEfectivo(celda) == CellType.NUMERIC && !DateUtil.isCellDateFormatted(celda)) {

            double valor = celda.getNumericCellValue();

            if (valor != Math.rint(valor) || Math.abs(valor) > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(columna.nombre + " no es un número entero");
            }

            return (int) valor;
        }

        String texto = limpiar(formateador.formatCellValue(celda));

        if (texto.isEmpty()) {
            if (obligatorio) {
                throw new IllegalArgumentException(columna.nombre + " vacío");
            }
            return null;
        }

        var decimales = ENTERO_CON_DECIMALES_VACIOS.matcher(texto);

        if (decimales.matches()) {
            texto = decimales.group(1);
        }

        if (!ENTERO.matcher(texto).matches()) {
            throw new IllegalArgumentException(columna.nombre + " no es un número");
        }

        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(columna.nombre + " no es un número");
        }
    }

    /** Fecha dd-MM-yyyy (texto) o una celda de fecha. Si no se entiende, queda sin fecha: no es un dato crítico. */
    private static LocalDate fecha(Cell celda, DataFormatter formateador) {

        if (celda == null) {
            return null;
        }

        if (tipoEfectivo(celda) == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalDate();
        }

        String texto = limpiar(formateador.formatCellValue(celda));

        if (texto.isEmpty() || texto.equals("-")) {
            return null;
        }

        for (DateTimeFormatter formato : List.of(FECHA_GUIONES, FECHA_BARRAS)) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException e) {
                // se prueba el siguiente formato
            }
        }

        return null;
    }

    private static CellType tipoEfectivo(Cell celda) {
        return celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
    }

}
