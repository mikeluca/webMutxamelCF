package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PendientesResponse;

/** Lo que la cuenta tiene por hacer según sus roles (docs/api-app-inicio-pendientes.md). Solo lee datos que ya existen. */
public interface PendientesService {

    String LISTAS_SIN_PASAR = "LISTAS_SIN_PASAR";
    String CONVOCATORIAS_SIN_PUBLICAR = "CONVOCATORIAS_SIN_PUBLICAR";
    String RESULTADOS_PENDIENTES = "RESULTADOS_PENDIENTES";
    String SOLICITUDES_FAMILIA = "SOLICITUDES_FAMILIA";
    String JUGADORES_A_LIBERAR = "JUGADORES_A_LIBERAR";
    String INVITACIONES_POR_APROBAR = "INVITACIONES_POR_APROBAR";
    String INVITACIONES_SIN_LIBERAR = "INVITACIONES_SIN_LIBERAR";
    String MODERACION = "MODERACION";
    String USUARIOS_CADUCADAS = "USUARIOS_CADUCADAS";
    String IMAGENES_PREVIA = "IMAGENES_PREVIA";
    String IMAGENES_RESULTADOS = "IMAGENES_RESULTADOS";

    /** Los grupos con algo pendiente; una cuenta solo de familia o jugador recibe la lista vacía. */
    PendientesResponse obtener(Long usuarioAppId);
}
