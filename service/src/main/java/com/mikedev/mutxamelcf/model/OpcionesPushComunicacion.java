package com.mikedev.mutxamelcf.model;

import java.util.Map;

/**
 * Ajustes del push de una comunicación privada que difieren de la
 * comunicación en sí.
 *
 * @param contenidoPush texto del push; {@code null} para usar el contenido de
 *                      la comunicación (por ejemplo, para que un dato sensible
 *                      solo se vea dentro de la app y no en la pantalla
 *                      bloqueada del móvil)
 * @param datos         pares extra del payload de datos; si lleva "tipo",
 *                      sustituye al "COMUNICACION" por defecto para que la app
 *                      navegue según el tipo y no según el título
 */
public record OpcionesPushComunicacion(String contenidoPush, Map<String, String> datos) {

    public OpcionesPushComunicacion {
        datos = datos == null ? Map.of() : Map.copyOf(datos);
    }
}
