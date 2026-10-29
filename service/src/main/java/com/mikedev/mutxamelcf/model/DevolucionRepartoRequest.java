package com.mikedev.mutxamelcf.model;

import java.util.List;

/** Jugadores asignados por error a un equipo definitivo, que vuelven a la pretemporada de su categoría. */
public record DevolucionRepartoRequest(List<Long> jugadorIds) {
}
