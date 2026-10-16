package com.mikedev.mutxamelcf.model;

/**
 * Problema detectado al leer una línea del CSV. El mensaje nunca contiene
 * el texto de la línea (podría incluir datos personales, como el árbitro).
 */
public record FfcvErrorLinea(int linea, String mensaje) {
}
