package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.ProximosPartidosResponse;

/** Lo que muestra Inicio en la app (docs/api-app-inicio-pendientes.md). Solo lee datos que ya existen. */
public interface InicioAppService {

    /** Ventana de búsqueda del próximo partido. */
    int DIAS_VENTANA = 14;

    /** Como mucho este número de entradas. */
    int MAXIMO_ENTRADAS = 6;

    /**
     * Por cada persona y equipo de la cuenta (jugador, hijo activo, equipo que entrena), su próximo partido que aún no
     * ha empezado en los próximos 14 días. Cuenta sin ninguna: lista vacía.
     */
    ProximosPartidosResponse obtenerProximosPartidos(Long usuarioAppId);
}
