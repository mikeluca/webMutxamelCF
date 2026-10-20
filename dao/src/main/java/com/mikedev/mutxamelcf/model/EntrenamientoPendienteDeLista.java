package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Un entrenamiento cuya lista nadie ha revisado todavía y del que no se ha
 * enviado aviso, con lo que hace falta para decidir si toca avisar: la hora y el
 * estado de su sesión del calendario (null si el entrenamiento no tiene sesión).
 */
public record EntrenamientoPendienteDeLista(
        Long id,
        Long equipoId,
        LocalDate fecha,
        String hora,
        String estadoSesion) {
}
