package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.Equipo;

/**
 * Quién puede ser invitado a la convocatoria de quién (docs/convocatoria-invitados.md, §1). Solo decide por el equipo
 * de destino y el de origen: no mira jugadores ni partidos.
 *
 * <ul>
 * <li>Dentro de una categoría formativa el nivel es A &gt; C &gt; B &gt; D y solo se sube (el origen es de un grupo
 * de menos nivel que el destino).</li>
 * <li>De la categoría inmediatamente inferior (según CATEGORIAS_EDAD.ORDEN) vale cualquier equipo.</li>
 * <li>Filial acepta a cualquier equipo de Juvenil; Primer Equipo, a Filial y a cualquier equipo de Juvenil.</li>
 * <li>No participan los equipos de pretemporada, los de grupo Esc o Fem ni Veteranos (ni como destino ni como
 * origen).</li>
 * </ul>
 */
@Component
public class ReglaInvitados {

    /** Máximo de invitados por convocatoria; no cuentan los rechazados ni los desestimados por la familia. */
    public static final int MAXIMO_INVITADOS = 3;

    static final String JUVENIL = "juvenil";
    static final String FILIAL = "filial";
    static final String PRIMER_EQUIPO = "primer equipo";
    static final String VETERANOS = "veteranos";

    /** Lo que la regla necesita saber de un equipo. */
    public record EquipoRegla(Long id, String categoria, String grupo, boolean pretemporada) {

        public static EquipoRegla de(Equipo equipo, boolean pretemporada) {
            return new EquipoRegla(equipo.getId(), equipo.getCategoria(), equipo.getGrupo(), pretemporada);
        }
    }

    /** El equipo puede participar, como destino o como origen, en una invitación. */
    public boolean participa(EquipoRegla equipo) {

        if (equipo == null || equipo.pretemporada()) {
            return false;
        }

        String grupo = normalizar(equipo.grupo());

        if (grupo.equals("esc") || grupo.equals("fem") || grupo.equals("p")) {
            return false;
        }

        return !normalizar(equipo.categoria()).equals(VETERANOS);
    }

    /**
     * true si el equipo de destino puede invitar a jugadores del de origen.
     *
     * @param categorias las categorías con regla de edad (CATEGORIAS_EDAD), para saber cuál es la inmediatamente
     *                   inferior
     */
    public boolean puedeInvitar(EquipoRegla destino, EquipoRegla origen, List<CategoriaEdad> categorias) {

        if (destino == null || origen == null || !participa(destino) || !participa(origen)) {
            return false;
        }

        if (destino.id() != null && destino.id().equals(origen.id())) {
            return false;
        }

        String categoriaDestino = normalizar(destino.categoria());
        String categoriaOrigen = normalizar(origen.categoria());

        if (categoriaDestino.equals(PRIMER_EQUIPO)) {
            return categoriaOrigen.equals(FILIAL) || categoriaOrigen.equals(JUVENIL);
        }

        if (categoriaDestino.equals(FILIAL)) {
            return categoriaOrigen.equals(JUVENIL);
        }

        Integer ordenDestino = orden(categorias, categoriaDestino);
        Integer ordenOrigen = orden(categorias, categoriaOrigen);

        // Sin regla de edad (Filial, Primer Equipo, Veteranos...) no se invita a nadie más.
        if (ordenDestino == null || ordenOrigen == null) {
            return false;
        }

        if (ordenDestino.equals(ordenOrigen)) {
            int nivelOrigen = nivel(origen.grupo());
            return nivelOrigen > 0 && nivelOrigen < nivel(destino.grupo());
        }

        return ordenOrigen.equals(ordenInferior(categorias, ordenDestino));
    }

    /** El orden de la categoría inmediatamente inferior a la de ese orden, o null si es la primera. */
    private static Integer ordenInferior(List<CategoriaEdad> categorias, int orden) {

        Integer inferior = null;

        for (CategoriaEdad categoria : categorias) {

            Integer otro = categoria.getOrden();

            if (otro != null && otro < orden && (inferior == null || otro > inferior)) {
                inferior = otro;
            }
        }

        return inferior;
    }

    private static Integer orden(List<CategoriaEdad> categorias, String categoriaNormalizada) {

        for (CategoriaEdad categoria : categorias) {

            if (normalizar(categoria.getCategoria()).equals(categoriaNormalizada)) {
                return categoria.getOrden();
            }
        }

        return null;
    }

    /** Nivel de un grupo dentro de su categoría: A 4, C 3, B 2, D 1; 0 si no es un grupo con nivel. */
    static int nivel(String grupo) {

        return switch (normalizar(grupo)) {
            case "a" -> 4;
            case "c" -> 3;
            case "b" -> 2;
            case "d" -> 1;
            default -> 0;
        };
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }
}
