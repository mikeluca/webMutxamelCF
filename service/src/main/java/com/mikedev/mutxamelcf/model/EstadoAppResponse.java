package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Panel "Estado de la app" para ADMIN_APP: cuántas cuentas y dispositivos hay y quién no
 * recibirá avisos. Todo son recuentos; las listas de cuentas se piden aparte por filtro.
 *
 * @param activasSinDispositivo activas sin ningún dispositivo activo (no recibirán push)
 * @param activasSinNotificaciones activas con la preferencia general de notificaciones apagada
 * @param usuariosInactivos activas cuyo último acceso fue hace más de {@code diasInactividad}
 * @param dispositivosVersionAntigua dispositivos activos con una versión inferior a la mínima
 */
public record EstadoAppResponse(
        Cuentas cuentas,
        int activasSinDispositivo,
        int activasSinNotificaciones,
        int usuariosInactivos,
        int diasInactividad,
        int dispositivosVersionAntigua,
        List<VersionDispositivos> versiones,
        VersionMinima versionMinima) {

    public record Cuentas(int activas, int pendientes, int desactivadas) {
    }

    /** version: "Desconocida" si el dispositivo no la informó (apps anteriores a esta función). */
    public record VersionDispositivos(String plataforma, String version, int dispositivos, boolean porDebajoMinima) {
    }

    public record VersionMinima(String android, String ios) {
    }

    /** Una cuenta de las listas de seguimiento. */
    public record UsuarioEstado(
            long id,
            String nombre,
            String email,
            List<String> roles,
            LocalDateTime ultimoAcceso,
            List<String> versiones) {
    }
}
