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
}
