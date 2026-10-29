package com.mikedev.mutxamelcf.model;

/**
 * Resultado de pedir la confirmación de continuidad.
 *
 * @param filasCreadas        jugadores añadidos al borrador al generarlo
 * @param jugadoresPreguntados jugadores a los que se ha preguntado ahora (no se repite a los ya preguntados)
 * @param usuariosAvisados    cuentas de la app a las que se ha enviado el aviso
 */
public record ResultadoPreguntaContinuidadDTO(
		Long temporadaId,
		String temporada,
		int filasCreadas,
		int jugadoresPreguntados,
		int usuariosAvisados) {
}
