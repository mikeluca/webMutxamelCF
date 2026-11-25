package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que la cuenta tiene por hacer (GET /api/app/pendientes, docs/api-app-inicio-pendientes.md). Los grupos sin nada no
 * se devuelven.
 */
public record PendientesResponse(List<Grupo> grupos) {

    /** Como mucho este número de elementos por grupo; {@code cantidad} es el total. */
    public static final int MAXIMO_ELEMENTOS = 5;

    /** {@code titulo} va en el idioma de la cuenta; {@code cantidad} es el total, aunque haya menos elementos. */
    public record Grupo(String codigo, String titulo, int cantidad, List<Elemento> elementos) {
    }

    /**
     * {@code destino} es un código que la app sabe abrir (PASAR_LISTA:12...); {@code diasRetraso} solo en los
     * resultados pendientes.
     */
    public record Elemento(Long id, String texto, LocalDate fecha, String destino, Integer diasRetraso) {
    }
}
