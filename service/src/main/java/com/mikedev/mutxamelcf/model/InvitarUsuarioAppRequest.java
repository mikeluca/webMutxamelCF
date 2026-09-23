package com.mikedev.mutxamelcf.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Petición de SUPER para invitar a una persona del club a crearse
 * una cuenta en la app móvil, con uno o varios vínculos/roles a la
 * vez (p. ej. jugador y familiar, o entrenador y coordinador).
 *
 * email: solo se usa el que envía el cliente si ningún vínculo es
 * FAMILIAR; si alguno de los vínculos es FAMILIAR, el servicio
 * ignora este campo y usa siempre el email registrado en la ficha
 * de ese familiar, por eso aquí no lleva @NotBlank/@Email (la
 * validación depende de los vínculos y se hace en el servicio).
 */
public class InvitarUsuarioAppRequest {

    @NotEmpty(message = "Debes seleccionar al menos un rol/vínculo")
    @Valid
    private List<VinculoSolicitado> vinculos;

    private String email;

    public InvitarUsuarioAppRequest() {
    }

    public List<VinculoSolicitado> getVinculos() {
        return vinculos;
    }

    public void setVinculos(List<VinculoSolicitado> vinculos) {
        this.vinculos = vinculos;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
