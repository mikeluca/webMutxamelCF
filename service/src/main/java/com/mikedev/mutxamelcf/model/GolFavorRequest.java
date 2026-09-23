package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Autor de un gol a favor del Mutxamel CF, para el aviso en directo y
 * la lista de goleadores del mensaje de "Final de partido".
 */
public class GolFavorRequest {

    @NotBlank(message = "El autor del gol es obligatorio")
    private String autor;

    public GolFavorRequest() {
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }
}
