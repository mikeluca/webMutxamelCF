package com.mikedev.mutxamelcf.mvc.communication;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mikedev.mutxamelcf.model.IdiomaApp;

/**
 * Plantillas de los emails a usuarios de la app: {@code emails/emails_es|ca|en.properties} (UTF-8). Los marcadores son
 * con nombre ({@code {codigo}}) y se sustituyen tal cual (no es MessageFormat); {@code \n} es un salto de línea.
 */
final class PlantillasEmail {

    private static final Pattern MARCADOR = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private static final Map<String, Properties> PLANTILLAS = new HashMap<>();

    static {
        for (String idioma : IdiomaApp.TODOS) {
            PLANTILLAS.put(idioma, cargar(idioma));
        }
    }

    private PlantillasEmail() {
    }

    private static Properties cargar(String idioma) {

        String recurso = "/emails/emails_" + idioma + ".properties";

        try (InputStream entrada = PlantillasEmail.class.getResourceAsStream(recurso)) {

            if (entrada == null) {
                throw new IllegalStateException("Falta la plantilla de emails " + recurso);
            }

            Properties propiedades = new Properties();
            propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return propiedades;

        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer la plantilla de emails " + recurso, e);
        }
    }

    static boolean existe(String idioma, String clave) {
        return PLANTILLAS.get(IdiomaApp.normalizar(idioma)).containsKey(clave);
    }

    /** El texto de la clave en el idioma, con los marcadores sustituidos (pares nombre-valor). */
    static String texto(String idioma, String clave, String... parametros) {

        String plantilla = PLANTILLAS.get(IdiomaApp.normalizar(idioma)).getProperty(clave);

        if (plantilla == null) {
            throw new IllegalArgumentException("No existe la plantilla de email '" + clave + "' en '" + idioma + "'");
        }

        Map<String, String> valores = new HashMap<>();

        for (int i = 0; i + 1 < parametros.length; i += 2) {
            valores.put(parametros[i], parametros[i + 1] == null ? "" : parametros[i + 1]);
        }

        // Una sola pasada: lo que se sustituye no se vuelve a interpretar.
        Matcher matcher = MARCADOR.matcher(plantilla);
        StringBuilder resultado = new StringBuilder();

        while (matcher.find()) {
            String valor = valores.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(valor));
        }

        matcher.appendTail(resultado);

        return resultado.toString();
    }
}
