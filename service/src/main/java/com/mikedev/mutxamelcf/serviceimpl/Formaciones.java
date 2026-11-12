package com.mikedev.mutxamelcf.serviceimpl;

import java.util.Arrays;
import java.util.List;

/**
 * Formaciones admitidas por modalidad y posición en el campo de cada titular. Una formación es "defensas-medios-
 * delanteros" (con cualquier número de líneas) y el equipo lleva además un portero: titulares = 1 + suma de la
 * formación.
 */
final class Formaciones {

    /** F11 (y modalidad desconocida). */
    static final List<String> F11 = List.of(
            "4-4-2", "4-3-3", "4-2-3-1", "4-5-1", "4-1-4-1", "3-5-2", "3-4-3", "5-3-2", "5-4-1");

    static final List<String> F8 = List.of("3-3-1", "3-2-2", "2-3-2", "2-4-1", "3-1-3", "4-2-1");

    static final List<String> F5 = List.of("2-2", "1-2-1", "2-1-1", "1-1-2", "3-1");

    private Formaciones() {
    }

    static List<String> permitidas(String modalidad) {

        if ("F8".equals(modalidad)) {
            return F8;
        }

        return "F5".equals(modalidad) ? F5 : F11;
    }

    /** Número de titulares que exige la formación: el portero más todos los de campo. */
    static int titulares(String formacion) {
        return 1 + Arrays.stream(formacion.split("-")).mapToInt(Integer::parseInt).sum();
    }

    /**
     * Posición en el campo del titular número {@code orden} (desde 1): {fila, columna}. Fila 1 es el portero (columna
     * 1); las siguientes son las líneas de la formación de atrás adelante y, dentro de cada una, la columna va de
     * izquierda a derecha por orden de elección.
     */
    static int[] posicion(String formacion, int orden) {

        if (orden == 1) {
            return new int[] { 1, 1 };
        }

        int restantes = orden - 1;
        String[] lineas = formacion.split("-");

        for (int i = 0; i < lineas.length; i++) {

            int jugadores = Integer.parseInt(lineas[i]);

            if (restantes <= jugadores) {
                return new int[] { i + 2, restantes };
            }

            restantes -= jugadores;
        }

        throw new IllegalArgumentException("El titular " + orden + " no cabe en la formación " + formacion);
    }
}
