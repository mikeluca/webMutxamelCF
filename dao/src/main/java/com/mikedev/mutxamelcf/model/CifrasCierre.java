package com.mikedev.mutxamelcf.model;

/**
 * Recuentos de lo que haría "Cerrar temporada" en la base de datos, para la pantalla de resumen.
 *
 * @param fotoCuerpoTecnico        filas de CUERPO_TECNICO_EQUIPO que se copian a CUERPO_TECNICO_TEMPORADA
 * @param cuerpoTecnicoDesasignado filas de CUERPO_TECNICO_EQUIPO que se borran (todas salvo las de equipos de adultos)
 * @param equiposAdultos           equipos de categorías sin regla de edad: pasan activos a la temporada nueva
 * @param equiposInactivos         el resto de equipos: se copian con ACTIVO = 0
 * @param horariosActivos          horarios activos de la temporada que se cierra (se desactivan y se copian)
 * @param sesionesABorrar          sesiones de entrenamiento desde el inicio de la nueva temporada sin entrenamiento
 * @param conceptosACopiar         conceptos de pago activos que todavía no están en la temporada nueva
 * @param notificaciones           notificaciones de la app que se borran
 * @param usuariosActivos          cuentas activas de la app: reciben el aviso de cambio de temporada
 */
public record CifrasCierre(
		int fotoCuerpoTecnico,
		int cuerpoTecnicoDesasignado,
		int equiposAdultos,
		int equiposInactivos,
		int horariosActivos,
		int sesionesABorrar,
		int conceptosACopiar,
		int notificaciones,
		int usuariosActivos) {
}
