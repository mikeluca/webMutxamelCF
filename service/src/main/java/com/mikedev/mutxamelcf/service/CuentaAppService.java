package com.mikedev.mutxamelcf.service;

public interface CuentaAppService {

    /**
     * Borra la cuenta de app del propio usuario (Apple 5.1.1(v)).
     *
     * Exige la contraseña actual. Elimina dispositivos FCM,
     * preferencias, notificaciones, roles y vínculos con las fichas
     * del club, y anonimiza la fila de USUARIOS_APP (no se borra
     * porque otras tablas la referencian). Las fichas de jugador,
     * familiar y cuerpo técnico se conservan: son datos del club.
     *
     * @throws IllegalArgumentException si el usuario no existe o la
     *                                  contraseña no es correcta
     */
    void eliminarCuenta(int usuarioAppId, String password);

    /**
     * Lo mismo que {@link #eliminarCuenta} sin pedir la contraseña ni exigir que la cuenta esté activa: para limpiar por
     * decisión del sistema una cuenta que nunca llegó a usarse (p. ej. la que creó una solicitud de familia que caducó).
     * El que llama comprueba antes que la cuenta no tiene roles ni vínculos.
     */
    void eliminarCuentaPorSistema(int usuarioAppId);
}
