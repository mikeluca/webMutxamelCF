package com.mikedev.mutxamelcf.serviceimpl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Las reglas del alias que se ve en el ranking del juego de Mutxigol (hay menores: nunca el nombre real):
 * de 3 a 15 caracteres (letras con acentos, números, espacio, guion y guion bajo), que no parezca un email ni un
 * teléfono y que no lleve palabras no permitidas.
 *
 * <p>
 * Las palabras no permitidas salen de {@code juego/palabras-no-permitidas.txt} (la lista básica, en el jar) más, si
 * está configurado {@code juego.alias.palabras-extra}, un fichero externo que se vuelve a leer cuando cambia: se amplía
 * sin recompilar ni reiniciar.
 */
@Component
public class AliasJuego {

    private static final Logger logger = LoggerFactory.getLogger(AliasJuego.class);

    public static final int LONGITUD_MINIMA = 3;
    public static final int LONGITUD_MAXIMA = 15;

    /** Con este número de dígitos o más, el alias parece un teléfono. */
    static final int DIGITOS_DE_TELEFONO = 6;

    /** Las palabras con al menos estas letras no pueden ir dentro del alias; las más cortas, solo como palabra entera. */
    static final int LETRAS_PARA_BUSCAR_DENTRO = 6;

    private static final String LISTA_BASICA = "juego/palabras-no-permitidas.txt";

    private static final Pattern CARACTERES_VALIDOS = Pattern.compile("^[\\p{L}\\p{N} _-]+$");
    private static final Pattern PARECE_EMAIL = Pattern.compile(
            "@|\\b(arroba|gmail|hotmail|outlook|yahoo|icloud)\\b|\\bpunto\\s*(com|es|net|org)\\b",
            Pattern.CASE_INSENSITIVE);

    private final Path ficheroExtra;

    private final Set<String> basicas;
    private volatile Set<String> extra = Set.of();
    private volatile long extraModificado = -1;

    public AliasJuego(@Value("${juego.alias.palabras-extra:}") String rutaExtra) {
        this.basicas = leerBasicas();
        this.ficheroExtra = rutaExtra == null || rutaExtra.isBlank() ? null : Path.of(rutaExtra.trim());
    }

    /** El alias sin espacios de más (sin cambiar mayúsculas ni acentos): lo que se guarda y se enseña. */
    public static String limpiar(String alias) {
        return alias == null ? null : alias.trim().replaceAll("\\s+", " ");
    }

    /** La clave de unicidad: mayúsculas, sin acentos y sin espacios de más. "Mútxi" y "MUTXI " son el mismo alias. */
    public static String normalizar(String alias) {
        String limpio = limpiar(alias);

        if (limpio == null) {
            return null;
        }

        return sinAcentos(limpio).toUpperCase(Locale.ROOT);
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /**
     * Valida el alias (ya limpio) y lo devuelve; {@code IllegalArgumentException} con el motivo (400) si no vale. La
     * unicidad la comprueba el servicio.
     */
    public String validar(String alias) {

        String limpio = limpiar(alias);

        if (limpio == null || limpio.isEmpty()) {
            throw new IllegalArgumentException("Escribe un alias");
        }

        if (limpio.codePointCount(0, limpio.length()) < LONGITUD_MINIMA
                || limpio.codePointCount(0, limpio.length()) > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException(
                    "El alias tiene que tener entre " + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA + " caracteres");
        }

        if (PARECE_EMAIL.matcher(limpio).find()) {
            throw new IllegalArgumentException("El alias no puede parecer un email");
        }

        if (digitos(limpio) >= DIGITOS_DE_TELEFONO) {
            throw new IllegalArgumentException("El alias no puede parecer un teléfono");
        }

        if (!CARACTERES_VALIDOS.matcher(limpio).matches()) {
            throw new IllegalArgumentException(
                    "El alias solo puede llevar letras, números, espacios, guiones y guiones bajos");
        }

        if (limpio.chars().noneMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("El alias tiene que llevar letras o números");
        }

        if (llevaPalabraNoPermitida(limpio)) {
            throw new IllegalArgumentException("Ese alias no está permitido, elige otro");
        }

        return limpio;
    }

    private static int digitos(String texto) {
        return (int) texto.chars().filter(Character::isDigit).count();
    }

    // ------------------------------------------------------------------
    // Palabras no permitidas
    // ------------------------------------------------------------------

    private static final String CIFRAS_POR_LETRAS_ORIGEN = "013457@$";
    private static final String CIFRAS_POR_LETRAS_DESTINO = "oieastas";

    /** Minúsculas, sin acentos y con las cifras que imitan letras cambiadas por ellas (0=o, 1=i, 3=e, 4=a, 5=s, 7=t). */
    static String paraComparar(String texto) {
        String base = sinAcentos(texto.toLowerCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder(base.length());

        for (char c : base.toCharArray()) {
            int i = CIFRAS_POR_LETRAS_ORIGEN.indexOf(c);
            sb.append(i >= 0 ? CIFRAS_POR_LETRAS_DESTINO.charAt(i) : c);
        }

        return sb.toString();
    }

    boolean llevaPalabraNoPermitida(String alias) {

        String comparable = paraComparar(alias);
        // Palabras enteras (separadas por lo que no es letra) y todo junto, sin separadores (para "p u t a").
        List<String> palabras = new ArrayList<>();

        for (String p : comparable.split("[^a-z]+")) {
            if (!p.isEmpty()) {
                palabras.add(p);
            }
        }

        String junto = comparable.replaceAll("[^a-z]", "");

        for (String prohibida : palabrasNoPermitidas()) {

            if (prohibida.length() >= LETRAS_PARA_BUSCAR_DENTRO) {
                if (junto.contains(prohibida)) {
                    return true;
                }
            } else if (palabras.contains(prohibida) || junto.equals(prohibida)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> palabrasNoPermitidas() {
        recargarExtraSiCambio();

        if (extra.isEmpty()) {
            return basicas;
        }

        Set<String> todas = new HashSet<>(basicas);
        todas.addAll(extra);

        return todas;
    }

    private void recargarExtraSiCambio() {

        if (ficheroExtra == null) {
            return;
        }

        try {
            long modificado = Files.exists(ficheroExtra) ? Files.getLastModifiedTime(ficheroExtra).toMillis() : -2;

            if (modificado == extraModificado) {
                return;
            }

            extraModificado = modificado;
            extra = modificado == -2 ? Set.of() : leer(Files.readAllBytes(ficheroExtra));

            logger.info("Lista extra de palabras no permitidas del juego: {} palabras", extra.size());

        } catch (IOException e) {
            logger.warn("No se ha podido leer la lista extra de palabras no permitidas del juego: {}", e.getMessage());
        }
    }

    private static Set<String> leerBasicas() {
        try (InputStream in = new ClassPathResource(LISTA_BASICA).getInputStream()) {
            return leer(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("No se encuentra " + LISTA_BASICA, e);
        }
    }

    private static Set<String> leer(byte[] contenido) {
        Set<String> palabras = new HashSet<>();

        for (String linea : new String(contenido, StandardCharsets.UTF_8).split("\\R")) {
            String texto = linea.trim();

            if (texto.isEmpty() || texto.startsWith("#")) {
                continue;
            }

            String palabra = paraComparar(texto).replaceAll("[^a-z]", "");

            if (!palabra.isEmpty()) {
                palabras.add(palabra);
            }
        }

        return Set.copyOf(palabras);
    }
}
