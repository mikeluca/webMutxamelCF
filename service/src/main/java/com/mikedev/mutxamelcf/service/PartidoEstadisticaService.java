package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PartidoEstadisticasGuardarRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasResponse;

public interface PartidoEstadisticaService {

    /**
     * Guarda (alta o reemplazo completo si ya existían) el resultado
     * numérico y las estadísticas por jugador de un partido ya jugado.
     * Mismo control de permisos que el resto de operaciones "App"
     * (EquipoGestionDao.puedeGestionarEquipo). La lista de jugadores debe
     * cubrir exactamente el mismo conjunto de jugadores que la
     * convocatoria del partido si existe, o el roster completo del
     * equipo si no la tiene.
     */
    PartidoEstadisticasResponse guardar(
            Long usuarioAppId,
            Long partidoId,
            PartidoEstadisticasGuardarRequest request);

    /**
     * Estadísticas actualmente guardadas para un partido. Si todavía no
     * se ha introducido el resultado, se devuelve con golesFavor/
     * golesContra/resultado a {@code null} y la lista de jugadores
     * vacía.
     */
    PartidoEstadisticasResponse obtener(
            Long usuarioAppId,
            Long partidoId);
}
