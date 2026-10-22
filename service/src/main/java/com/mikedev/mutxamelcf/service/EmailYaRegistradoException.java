package com.mikedev.mutxamelcf.service;

/**
 * Ya existe una cuenta de la app con ese email. Es un {@link IllegalArgumentException}
 * (el panel web lo trata como cualquier dato no válido); la app lo distingue para
 * responder 409.
 */
public class EmailYaRegistradoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public EmailYaRegistradoException() {
        super("Ya existe un usuario con ese email");
    }
}
