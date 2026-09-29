package com.mikedev.mutxamelcf.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Autor de un gol a favor del Mutxamel CF, para el aviso en directo y
 * la lista de goleadores del mensaje de "Final de partido".
 */
public class GolFavorRequest {

    @NotBlank(message = "El autor del gol es obligatorio")
    @Size(max = 100, message = "El nombre del autor del gol es demasiado largo")
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
