package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Alineación elegida entre los jugadores del partido: los ids de los titulares y de los suplentes, cada lista en el
 * orden en que se eligieron (con formación, el de los titulares da la posición en el campo: portero, líneas de atrás
 * adelante y, en cada línea, de izquierda a derecha). Los suplentes son opcionales (lista vacía o ausente). Las reglas
 * (titulares no vacío, sin repetidos, solo jugadores elegibles, máximo de titulares, formación de la lista cerrada y
 * titulares = 1 + suma de la formación) las valida el servicio.
 *
 * <p>
 * {@code formacion} es opcional ("4-4-2"). {@code avisar} decide si se envía el push de la alineación: solo se envía
 * si es {@code true} (ausente cuenta como {@code false}).
 */
public class AlineacionJugadoresRequest {

    private List<Long> titulares;
    private List<Long> suplentes;
    private String formacion;
    private Boolean avisar;

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

    public String getFormacion() {
        return formacion;
    }

    public void setFormacion(String formacion) {
        this.formacion = formacion;
    }

    public Boolean getAvisar() {
        return avisar;
    }

    public void setAvisar(Boolean avisar) {
        this.avisar = avisar;
    }
}
