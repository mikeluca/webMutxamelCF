package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * La petición de acceso de un familiar. En el registro público (sin sesión) lleva todos los datos del familiar; con la
 * sesión iniciada, {@code nombre}, {@code apellidos} y {@code email} se ignoran o se completan con los de la cuenta.
 *
 * @param parentesco MADRE, PADRE, TUTOR u OTRO
 * @param hijos      de 1 a 5; solo nombre, apellidos y equipo o categoría tal como los escribe la familia
 * @param idioma     es, ca o en: el idioma de la cuenta y del email (opcional)
 */
public record SolicitudFamiliaRequest(
        String nombre,
        String apellidos,
        String email,
        String telefono,
        String parentesco,
        List<Hijo> hijos,
        Boolean aceptaPrivacidad,
        Boolean declaraRepresentante,
        String idioma) {

    /** Un hijo: nada de fecha de nacimiento ni otros datos del menor. */
    public record Hijo(String nombre, String apellidos, String equipo) {
    }
}
