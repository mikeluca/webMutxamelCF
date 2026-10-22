package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;

/**
 * Cola de moderación de mensajes reportados. Solo ADMIN_APP puede consultarla y
 * resolverla (SecurityException al resto); registrar un reporte lo hace cualquier
 * usuario que pueda ver el mensaje.
 */
public interface ModeracionService {

    /**
     * Guarda el reporte con una copia del mensaje y avisa por push a ADMIN_APP
     * (tras el commit). Devuelve el id del reporte, o {@code null} si ese usuario
     * ya había reportado ese mensaje (no se duplica nada).
     */
    Long registrarReporte(Comunicacion comunicacion, Long reportadorId, String motivo);

    /** estado: "PENDIENTE" (por defecto) o "RESUELTOS" (los 50 últimos). */
    List<ReporteModeracionResponse> listar(Long moderadorId, String estado);

    int contarPendientes(Long moderadorId);

    void descartar(Long moderadorId, Long reporteId, String nota);

    void borrarMensaje(Long moderadorId, Long reporteId, String nota);

    void desactivarAutor(Long moderadorId, Long reporteId, String nota, boolean borrarMensaje);

    /**
     * Reactiva la cuenta del autor de un reporte en AUTOR_DESACTIVADO cuyo autor sigue desactivado
     * (con la misma lógica que activar desde el panel web). El reporte sigue en AUTOR_DESACTIVADO,
     * pero se anota en su nota de resolución quién la reactivó y cuándo. Sus dispositivos no se
     * reactivan: se registran al volver a entrar. Devuelve el id del autor.
     *
     * {@link IllegalStateException} (409) si el reporte está en otro estado o el autor ya está
     * activo; {@link java.util.NoSuchElementException} si el reporte no existe.
     */
    Long reactivarAutor(Long moderadorId, Long reporteId, String nota);
}
