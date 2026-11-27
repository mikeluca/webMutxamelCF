package com.mikedev.mutxamelcf.mvc.controller;

/**
 * El texto del contador del filtro de Equipo de los listados del panel ("12 jugadores en Alevín B", "1 familiar de
 * jugadores de Alevín B"), ya escrito en la página al cargar. El navegador lo recalcula al filtrar con el mismo formato
 * (static/js/filtros-panel.js, textoContador).
 */
final class ContadorFiltro {

    private ContadorFiltro() {
    }

    static String texto(int total, String uno, String varios, String conector, String nombreEquipo) {
        return (total == 1 ? "1 " + uno : total + " " + varios) + " " + conector + " " + nombreEquipo;
    }
}
