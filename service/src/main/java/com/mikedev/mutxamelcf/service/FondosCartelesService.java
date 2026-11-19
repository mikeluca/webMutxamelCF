package com.mikedev.mutxamelcf.service;

import java.time.LocalDateTime;

import com.mikedev.mutxamelcf.model.CartelRedes;

/**
 * Los fondos de los carteles para redes sociales: el que sube el club desde el panel (Administración deportiva →
 * Fondos de carteles) o, si no hay ninguno, el de por defecto de resources.
 */
public interface FondosCartelesService {

    int MAXIMO_BYTES = 5 * 1024 * 1024;
    int ANCHO_MINIMO = 1080;
    int ALTO_MINIMO = 1350;

    /**
     * El fondo vigente de un cartel.
     *
     * @param personalizado true si viene de la tabla, false si es el de por defecto
     * @param fecha         cuándo se subió (null en el de por defecto)
     * @param usuario       quién lo subió (null en el de por defecto o si no se guardó)
     */
    record Fondo(CartelRedes cartel, byte[] bytes, String tipoContenido, int ancho, int alto, boolean personalizado,
            LocalDateTime fecha, String usuario) {
    }

    /** El fondo vigente, o null si el cartel aún no tiene ninguno (FILIAL). */
    Fondo actual(CartelRedes cartel);

    /**
     * Valida y guarda un fondo nuevo tal cual (con su ancho y alto).
     *
     * @throws IllegalArgumentException con un mensaje claro si el cartel aún no está disponible, o el archivo pesa
     *                                  más de 5 MB, no es un JPG o PNG válido o mide menos de 1080×1350
     */
    void subir(CartelRedes cartel, byte[] archivo, String usuario);

    /** Borra el fondo subido: vuelve el de por defecto. Devuelve si había alguno. */
    boolean restaurar(CartelRedes cartel);
}
