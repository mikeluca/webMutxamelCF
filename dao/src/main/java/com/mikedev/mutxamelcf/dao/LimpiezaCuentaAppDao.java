package com.mikedev.mutxamelcf.dao;

import java.util.Map;

/**
 * Lo que hay que mirar y borrar antes de eliminar una cuenta de la app (USUARIOS_APP): la actividad del club que la
 * referencia (no se borra) y los datos propios de la cuenta (sí se borran).
 */
public interface LimpiezaCuentaAppDao {

    /**
     * Filas de actividad del club que apuntan a la cuenta (respuestas de convocatoria, justificaciones, eventos en
     * directo, reportes, revisiones...), por descripción y solo las que tienen alguna. Vacío si la cuenta no tiene.
     */
    Map<String, Integer> contarActividad(int usuarioAppId);

    /**
     * Borra los datos propios de la cuenta (solicitudes de familia con sus hijos, notificaciones, preferencias,
     * dispositivos y destinatarios de comunicaciones). Devuelve las filas borradas por tabla, solo las que tenían alguna.
     */
    Map<String, Integer> borrarDatosPropios(int usuarioAppId);
}
