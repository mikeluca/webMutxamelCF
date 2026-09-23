package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Personas del club que todavía no tienen cuenta en la app móvil,
 * agrupadas por tipo, para alimentar el formulario de invitación.
 */
public class PersonasVinculablesResponse {

    private List<PersonaVinculable> jugadores;
    private List<PersonaVinculable> familiares;
    private List<PersonaVinculable> cuerpoTecnico;

    public PersonasVinculablesResponse() {
    }

    public PersonasVinculablesResponse(
            List<PersonaVinculable> jugadores,
            List<PersonaVinculable> familiares,
            List<PersonaVinculable> cuerpoTecnico) {

        this.jugadores = jugadores;
        this.familiares = familiares;
        this.cuerpoTecnico = cuerpoTecnico;
    }

    public List<PersonaVinculable> getJugadores() {
        return jugadores;
    }

    public void setJugadores(List<PersonaVinculable> jugadores) {
        this.jugadores = jugadores;
    }

    public List<PersonaVinculable> getFamiliares() {
        return familiares;
    }

    public void setFamiliares(List<PersonaVinculable> familiares) {
        this.familiares = familiares;
    }

    public List<PersonaVinculable> getCuerpoTecnico() {
        return cuerpoTecnico;
    }

    public void setCuerpoTecnico(List<PersonaVinculable> cuerpoTecnico) {
        this.cuerpoTecnico = cuerpoTecnico;
    }
}
