package com.mikedev.mutxamelcf.dao;

import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.ReporteComunicacion;

/** Cola de moderación: mensajes reportados por los usuarios. */
public interface ReporteComunicacionDao {

    /** Inserta el reporte (siempre PENDIENTE) y devuelve su id. */
    Long crear(ReporteComunicacion reporte);

    boolean existe(Long comunicacionId, Long reportadorId);

    ReporteComunicacion obtenerPorId(Long id);

    /** Pendientes, del más reciente al más antiguo. */
    List<ReporteComunicacion> listarPendientes();

    /** Los no pendientes: los {@code limite} últimos resueltos. */
    List<ReporteComunicacion> listarResueltos(int limite);

    int contarPendientes();

    /** Todos los reportes (pendientes y resueltos) del autor, salvo {@code excluirId}. */
    int contarDelAutor(Long autorId, Long excluirId);

    /**
     * Resuelve los reportes PENDIENTES de ese mensaje con el mismo estado; devuelve
     * cuántos.
     */
    int resolverPendientesDeComunicacion(
            Long comunicacionId, String estado, Long resueltoPorId, LocalDateTime fecha, String nota);

    /** Resuelve solo ese reporte si sigue pendiente; devuelve las filas cambiadas (0 o 1). */
    int resolver(Long id, String estado, Long resueltoPorId, LocalDateTime fecha, String nota);
}
