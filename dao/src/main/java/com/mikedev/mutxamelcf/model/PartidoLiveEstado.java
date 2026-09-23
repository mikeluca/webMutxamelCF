package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Marcador mínimo del partido en directo del primer equipo: goles a
 * favor/en contra y la lista de goleadores del Mutxamel CF, acumulados
 * entre el "Inicio de partido" que los reinicia y el "Final de partido".
 */
public class PartidoLiveEstado {

    private int golesFavor;
    private int golesContra;
    private List<String> goleadores;

    public PartidoLiveEstado() {
    }

    public PartidoLiveEstado(int golesFavor, int golesContra, List<String> goleadores) {
        this.golesFavor = golesFavor;
        this.golesContra = golesContra;
        this.goleadores = goleadores;
    }

    public int getGolesFavor() {
        return golesFavor;
    }

    public void setGolesFavor(int golesFavor) {
        this.golesFavor = golesFavor;
    }

    public int getGolesContra() {
        return golesContra;
    }

    public void setGolesContra(int golesContra) {
        this.golesContra = golesContra;
    }

    public List<String> getGoleadores() {
        return goleadores;
    }

    public void setGoleadores(List<String> goleadores) {
        this.goleadores = goleadores;
    }
}
