package com.mikedev.mutxamelcf.serviceimpl;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mikedev.mutxamelcf.model.JugadorCandidato;

/**
 * Qué jugadores activos coinciden con lo que ha escrito una familia en una solicitud: lógica pura, sin base de datos.
 *
 * <p>
 * El nombre y los apellidos se comparan normalizados (sin acentos, mayúsculas, signos ni espacios de más). Los
 * apellidos coinciden si son iguales o si uno es el principio del otro, así que "García" encuentra a "García Pérez"
 * (la familia no siempre escribe el segundo apellido). El equipo o la categoría que escribió la familia no excluye a
 * nadie, porque se escribe a menudo mal: solo marca {@code coincideEquipo} y pone esos primero.
 */
public final class CoincidenciasJugador {

    /** Un jugador que coincide; {@code coincideEquipo} solo cuando la familia escribió un equipo y es ese. */
    public record Coincidencia(JugadorCandidato jugador, boolean coincideEquipo) {
    }

    private CoincidenciasJugador() {
    }

    /** Minúsculas, sin acentos ni signos y con los espacios normalizados ("  Alevín  B. " es "alevin b"). */
    public static String normalizar(String texto) {

        if (texto == null) {
            return "";
        }

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9ñ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Los jugadores de {@code activos} que coinciden en nombre y apellidos, los del equipo escrito primero. */
    public static List<Coincidencia> buscar(String nombre, String apellidos, String equipoTexto,
            List<JugadorCandidato> activos) {

        String n = normalizar(nombre);
        String a = normalizar(apellidos);

        List<Coincidencia> encontradas = new ArrayList<>();

        if (n.isEmpty() || a.isEmpty()) {
            return encontradas;
        }

        for (JugadorCandidato j : activos) {

            if (n.equals(normalizar(j.nombre())) && coincidenApellidos(a, normalizar(j.apellidos()))) {
                encontradas.add(new Coincidencia(j, coincideEquipo(equipoTexto, j)));
            }
        }

        encontradas.sort(Comparator.comparing((Coincidencia c) -> !c.coincideEquipo())
                .thenComparing(c -> normalizar(c.jugador().apellidos()))
                .thenComparing(c -> c.jugador().id()));

        return encontradas;
    }

    /** Iguales, o uno empieza por el otro palabra a palabra ("garcia" y "garcia perez"). */
    static boolean coincidenApellidos(String escritos, String ficha) {

        if (escritos.isEmpty() || ficha.isEmpty()) {
            return false;
        }

        return escritos.equals(ficha) || (ficha + " ").startsWith(escritos + " ")
                || (escritos + " ").startsWith(ficha + " ");
    }

    /**
     * La familia escribió un equipo o una categoría y es el del jugador: el nombre del equipo ("Alevín B"), la categoría
     * ("Alevín") o parte del nombre del equipo (a partir de tres letras, para que "alev" no coincida con todo).
     */
    static boolean coincideEquipo(String equipoTexto, JugadorCandidato jugador) {

        String escrito = normalizar(equipoTexto);

        if (escrito.isEmpty()) {
            return false;
        }

        String equipo = normalizar(jugador.equipo());
        String categoria = normalizar(jugador.categoria());

        return (!equipo.isEmpty() && (equipo.equals(escrito) || (escrito.length() >= 3 && equipo.contains(escrito))))
                || (!categoria.isEmpty() && categoria.equals(escrito));
    }

    /** Los que contienen todas las palabras de {@code texto} en su nombre y apellidos, para buscar a mano. */
    public static List<JugadorCandidato> buscarPorTexto(String texto, List<JugadorCandidato> activos, int maximo) {

        String buscado = normalizar(texto);
        List<JugadorCandidato> resultado = new ArrayList<>();

        if (buscado.length() < 3) {
            return resultado;
        }

        for (JugadorCandidato j : activos) {

            String completo = normalizar(j.nombre() + " " + j.apellidos());
            boolean todas = true;

            for (String palabra : buscado.split(" ")) {
                if (!completo.contains(palabra)) {
                    todas = false;
                    break;
                }
            }

            if (todas) {
                resultado.add(j);

                if (resultado.size() >= maximo) {
                    break;
                }
            }
        }

        return resultado;
    }
}
