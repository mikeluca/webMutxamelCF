package com.mikedev.mutxamelcf.serviceimpl;

/**
 * Un aviso ya redactado en un idioma: el título, el contenido de la bandeja y, solo si el push lleva un texto distinto,
 * el del push ({@code null} = el mismo que la bandeja).
 */
record TextoAviso(String titulo, String contenido, String contenidoPush) {

    TextoAviso(String titulo, String contenido) {
        this(titulo, contenido, null);
    }
}
