package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Un vínculo a añadir o quitar de una cuenta de la app: tipoVinculo
 * (JUGADOR, FAMILIAR, ENTRENADOR o COORDINADOR) + la persona real del
 * club a la que se vincula (null para COORDINADOR, que es un rol de
 * club sin vínculo a una persona concreta).
 */
public class VinculoSolicitado {

    @NotBlank(message = "El tipo de vínculo es obligatorio")
    private String tipo;

    private Long personaId;

    public VinculoSolicitado() {
    }

    public VinculoSolicitado(String tipo, Long personaId) {
        this.tipo = tipo;
        this.personaId = personaId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public void setPersonaId(Long personaId) {
        this.personaId = personaId;
    }
}
