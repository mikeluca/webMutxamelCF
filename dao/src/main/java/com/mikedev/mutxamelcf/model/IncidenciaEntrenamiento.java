package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/** Una asistencia a un entrenamiento distinta de PRESENTE. */
public record IncidenciaEntrenamiento(LocalDate fecha, String estado) {
}
