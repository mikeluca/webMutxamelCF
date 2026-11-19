package com.mikedev.mutxamelcf.model;

/**
 * Los carteles para redes sociales que generan su propio diseño en el servidor y tienen fondo editable
 * (IMAGENES_REDES_FONDOS.CARTEL). Añadir uno nuevo: el valor aquí (con su fondo por defecto en resources) y el fondo en
 * la tabla; su diseño y su ruta se hacen aparte.
 */
public enum CartelRedes {

    PRIMER_EQUIPO("Primer Equipo", "/redes/cartel-primer-equipo/fondo.jpg"),

    /** El cartel del Filial: su propio diseño (CartelFilialRenderer) y su ruta; fondo por defecto en redes/cartel-filial. */
    FILIAL("Filial", "/redes/cartel-filial/fondo.jpg");

    private final String nombre;
    private final String fondoPorDefecto;

    CartelRedes(String nombre, String fondoPorDefecto) {
        this.nombre = nombre;
        this.fondoPorDefecto = fondoPorDefecto;
    }

    public String nombre() {
        return nombre;
    }

    /** El recurso del classpath con el fondo por defecto; null si el cartel aún no existe (ninguno hoy). */
    public String fondoPorDefecto() {
        return fondoPorDefecto;
    }

    /** true si el cartel ya está hecho (se puede cambiar su fondo y generarlo). */
    public boolean disponible() {
        return fondoPorDefecto != null;
    }

    /** El cartel con ese código (PRIMER_EQUIPO, FILIAL); {@link IllegalArgumentException} si no existe. */
    public static CartelRedes de(String codigo) {

        if (codigo != null) {
            for (CartelRedes cartel : values()) {
                if (cartel.name().equalsIgnoreCase(codigo.trim())) {
                    return cartel;
                }
            }
        }

        throw new IllegalArgumentException("El cartel no existe");
    }
}
