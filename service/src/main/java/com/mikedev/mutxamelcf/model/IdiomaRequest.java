package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /api/app/preferencias/idioma}: {"idioma":"ca"}. */
public class IdiomaRequest {

    /** "es", "ca" o "en"; cualquier otro valor se guarda como "es". */
    @Size(max = 20, message = "El idioma no puede superar los 20 caracteres")
    private String idioma;

    public IdiomaRequest() {
    }

    public String getIdioma() {
        return idioma;
    }

    public void setIdioma(String idioma) {
        this.idioma = idioma;
    }
}
