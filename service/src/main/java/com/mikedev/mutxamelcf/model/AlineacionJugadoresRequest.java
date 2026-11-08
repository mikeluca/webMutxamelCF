package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Alineación elegida entre los jugadores del partido: los ids de los titulares y de los suplentes, cada lista en el
 * orden en que debe salir en el aviso. Los suplentes son opcionales (lista vacía o ausente). Las reglas (titulares no
 * vacío, sin repetidos, solo jugadores elegibles, máximo de titulares) las valida el servicio.
 */
public class AlineacionJugadoresRequest {

    private List<Long> titulares;
    private List<Long> suplentes;

    public AlineacionJugadoresRequest() {
    }

    public List<Long> getTitulares() {
        return titulares;
    }

    public void setTitulares(List<Long> titulares) {
        this.titulares = titulares;
    }

    public List<Long> getSuplentes() {
        return suplentes;
    }

    public void setSuplentes(List<Long> suplentes) {
        this.suplentes = suplentes;
    }
}
