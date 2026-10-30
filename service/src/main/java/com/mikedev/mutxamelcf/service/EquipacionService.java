package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.Equipacion;
import com.mikedev.mutxamelcf.model.EquipacionResponse;

/**
 * Equipajes del club: los activos para elegir en la convocatoria (API de la app) y su alta y mantenimiento (panel web,
 * SUPER y ADMIN). Los errores de validación son {@link IllegalArgumentException} (400) y {@link SecurityException}
 * (403).
 */
public interface EquipacionService {

    /** Los equipajes activos por ORDEN y NOMBRE. Solo para quien gestiona algún equipo (403 si no). */
    List<EquipacionResponse> obtenerActivasParaApp(Long usuarioAppId);

    /** Todos, con cuántas convocatorias usa cada uno. */
    List<Equipacion> obtenerTodas();

    /** Crea (id null) o actualiza el equipaje y lo devuelve. */
    Equipacion guardar(Long id, String nombre, String camiseta, String pantalon, String medias, String colorHex,
            Integer orden);

    void cambiarActiva(Long id, boolean activa);

    /** Solo si ninguna convocatoria lo usa; si no, hay que desactivarlo (400). */
    void eliminar(Long id);
}
