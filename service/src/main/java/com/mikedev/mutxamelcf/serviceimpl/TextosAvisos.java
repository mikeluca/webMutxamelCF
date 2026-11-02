package com.mikedev.mutxamelcf.serviceimpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.IdiomaApp;

/**
 * Catálogo de los textos de los avisos automáticos (bandeja, push y emails), en castellano, valenciano e inglés.
 * Los textos viven en {@code i18n/avisos_es|ca|en.properties} (UTF-8).
 *
 * <p>
 * Los marcadores son con nombre ({@code {jugador}}, {@code {rival}}...) y NO son MessageFormat: se sustituyen tal
 * cual, así que un apóstrofo del valenciano no hay que duplicarlo. {@code \n} en el fichero es un salto de línea. Lo
 * que escribe la gente (motivos, nombres de equipos, rivales, equipajes...) entra como valor y no se traduce ni se
 * vuelve a interpretar.
 */
@Component
public class TextosAvisos {

    private static final Logger logger = LoggerFactory.getLogger(TextosAvisos.class);

    private static final Pattern MARCADOR = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private static final String RECURSO = "/i18n/avisos_%s.properties";

    private final Map<String, Properties> catalogos = new HashMap<>();

    public TextosAvisos() {

        for (String idioma : IdiomaApp.TODOS) {
            catalogos.put(idioma, cargar(idioma));
        }
    }

    private static Properties cargar(String idioma) {

        String recurso = String.format(RECURSO, idioma);

        try (InputStream entrada = TextosAvisos.class.getResourceAsStream(recurso)) {

            if (entrada == null) {
                throw new IllegalStateException("Falta el catálogo de avisos " + recurso);
            }

            Properties propiedades = new Properties();
            propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return propiedades;

        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer el catálogo de avisos " + recurso, e);
        }
    }

    /** Las claves del catálogo del idioma. */
    public Set<String> claves(String idioma) {
        return catalogos.get(IdiomaApp.normalizar(idioma)).stringPropertyNames();
    }

    /** La plantilla sin sustituir, o {@code null} si ni el idioma ni el castellano la tienen. */
    public String plantilla(String idioma, String clave) {

        String codigo = IdiomaApp.normalizar(idioma);
        String plantilla = catalogos.get(codigo).getProperty(clave);

        if (plantilla == null && !IdiomaApp.ES.equals(codigo)) {
            logger.warn("Falta la clave de aviso '{}' en '{}': se usa el castellano", clave, codigo);
            plantilla = catalogos.get(IdiomaApp.ES).getProperty(clave);
        }

        return plantilla;
    }

    /** Solo para los tests: simula un catálogo al que le falta una clave. */
    void quitarClaveSoloParaPruebas(String idioma, String clave) {
        catalogos.get(IdiomaApp.normalizar(idioma)).remove(clave);
    }

    public boolean existe(String clave) {
        return catalogos.get(IdiomaApp.ES).containsKey(clave);
    }

    /**
     * El texto de {@code clave} en el idioma, con los marcadores sustituidos por los {@code parametros} (pares
     * nombre-valor: {@code texto("ca", "falta.jugador", "fecha", "dissabte 25/10")}). Un marcador sin valor se deja
     * tal cual y se avisa en el log. Una clave que no existe es un error de programación.
     */
    public String texto(String idioma, String clave, String... parametros) {

        if (parametros.length % 2 != 0) {
            throw new IllegalArgumentException("Los parámetros van en pares nombre-valor");
        }

        Map<String, String> valores = new HashMap<>();

        for (int i = 0; i < parametros.length; i += 2) {
            valores.put(parametros[i], parametros[i + 1] == null ? "" : parametros[i + 1]);
        }

        return texto(idioma, clave, valores);
    }

    public String texto(String idioma, String clave, Map<String, String> parametros) {

        String plantilla = plantilla(idioma, clave);

        if (plantilla == null) {
            throw new IllegalArgumentException("No existe la clave de aviso '" + clave + "'");
        }

        // Una sola pasada: lo que se sustituye no se vuelve a interpretar (un nombre con "{x}" se queda como está).
        Matcher matcher = MARCADOR.matcher(plantilla);
        StringBuilder resultado = new StringBuilder();

        while (matcher.find()) {

            String valor = parametros.get(matcher.group(1));

            if (valor == null) {
                logger.warn("El aviso '{}' ({}) no recibe el marcador '{}'", clave, idioma, matcher.group());
                valor = matcher.group();
            }

            matcher.appendReplacement(resultado, Matcher.quoteReplacement(valor));
        }

        matcher.appendTail(resultado);

        return resultado.toString();
    }

    /**
     * El texto de la bandeja con el motivo que ha escrito una persona (familia, coordinación...) como párrafo aparte; sin
     * motivo, el texto tal cual. El motivo va SOLO en la bandeja: el push lleva el texto sin él.
     */
    public String conMotivo(String idioma, String texto, String motivo) {

        if (motivo == null || motivo.isBlank()) {
            return texto;
        }

        return texto + "\n\n" + texto(idioma, "motivo", "motivo", motivo.trim());
    }

    /**
     * Sin los espacios de los extremos ni los puntos finales sobrantes ("Lluvia. " -> "Lluvia"); vacío si no queda nada
     * o el motivo es nulo.
     */
    public static String sinPuntoFinal(String motivo) {

        if (motivo == null) {
            return "";
        }

        return motivo.replaceAll("[\\s.]+$", "").trim();
    }

    // ---------- Formatos que dependen del idioma ----------

    private static Locale locale(String idioma) {
        return Locale.forLanguageTag(IdiomaApp.normalizar(idioma));
    }

    /** {@code {fecha}}: día de la semana y d/M ("sábado 25/10", "dissabte 25/10", "Saturday 25/10"). */
    public String fecha(String idioma, LocalDate fecha) {
        return fecha == null ? "" : DateTimeFormatter.ofPattern("EEEE d/M", locale(idioma)).format(fecha);
    }

    /** {@code {fechaCompleta}}: d/M/aaaa. */
    public String fechaCompleta(String idioma, LocalDate fecha) {
        return fecha == null ? "" : DateTimeFormatter.ofPattern("d/M/yyyy", locale(idioma)).format(fecha);
    }

    /** {@code {hora}}: HH:mm, sin "h" detrás. */
    public String hora(LocalTime hora) {
        return hora == null ? "" : DateTimeFormatter.ofPattern("HH:mm").format(hora);
    }

    /** "A", "A y B", "A, B y C" con la palabra de lista.y del idioma. */
    public String lista(String idioma, Collection<String> nombres) {

        List<String> lista = nombres.stream().filter(n -> n != null && !n.isBlank()).toList();

        if (lista.isEmpty()) {
            return "";
        }

        if (lista.size() == 1) {
            return lista.get(0);
        }

        String y = texto(idioma, "lista.y");

        return String.join(", ", lista.subList(0, lista.size() - 1)) + " " + y + " " + lista.get(lista.size() - 1);
    }
}
