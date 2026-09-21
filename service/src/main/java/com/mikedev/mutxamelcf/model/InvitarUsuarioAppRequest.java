package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición de SUPER para invitar a una persona del club a crearse
 * una cuenta en la app móvil.
 *
 * tipoVinculo: JUGADOR, FAMILIAR, ENTRENADOR o COORDINADOR.
 * personaId: obligatorio salvo cuando tipoVinculo es COORDINADOR
 * (validado en el servicio, no aquí, porque depende de tipoVinculo).
 * email: solo se usa el que envía el cliente si tipoVinculo NO es
 * FAMILIAR; para FAMILIAR el servicio ignora este campo y usa
 * siempre el email registrado en la ficha del familiar, por eso
 * aquí no lleva @NotBlank/@Email (la validación depende del tipo
 * y se hace en el servicio).
 */
public class InvitarUsuarioAppRequest {

    @NotBlank(message = "El tipo de vínculo es obligatorio")
    private String tipoVinculo;

    private Long personaId;

    private String email;

    public InvitarUsuarioAppRequest() {
    }

    public String getTipoVinculo() {
        return tipoVinculo;
    }

    public void setTipoVinculo(String tipoVinculo) {
        this.tipoVinculo = tipoVinculo;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public void setPersonaId(Long personaId) {
        this.personaId = personaId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
