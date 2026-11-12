package com.mikedev.mutxamelcf.model;

/**
 * Un jugador de la ficha pública del partido: datos básicos y, solo si es mayor de edad, su foto a tamaño original
 * (se reduce a miniatura en el servicio). {@code foto} es null para un menor aunque tenga foto guardada: la consulta
 * ni la lee.
 */
public record JugadorFicha(JugadorBasico jugador, byte[] foto) {
}
