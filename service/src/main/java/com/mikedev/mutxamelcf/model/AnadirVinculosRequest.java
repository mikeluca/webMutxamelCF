package com.mikedev.mutxamelcf.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Petición de SUPER para añadir uno o varios vínculos/roles nuevos a
 * una cuenta de la app ya existente (p. ej. una cuenta que hoy solo
 * es jugador pasa a ser también entrenador). El email de la cuenta
 * nunca se toca aquí.
 */
public class AnadirVinculosRequest {

    @NotEmpty(message = "Debes seleccionar al menos un rol/vínculo")
    @Valid
    private List<VinculoSolicitado> vinculos;

    public AnadirVinculosRequest() {
    }

    public List<VinculoSolicitado> getVinculos() {
        return vinculos;
    }

    public void setVinculos(List<VinculoSolicitado> vinculos) {
        this.vinculos = vinculos;
    }
}
