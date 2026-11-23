package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * Una cuenta en el listado de usuarios para ADMIN_APP desde la app (GET /api/app/admin/usuarios): lo mismo que la
 * tabla del panel web. {@code estado}: ACTIVA | PENDIENTE | CADUCADA | DESACTIVADA (la misma regla que las insignias
 * del panel).
 */
public record UsuarioAdminApp(
        int id,
        String email,
        String nombre,
        String estado,
        boolean activo,
        List<Rol> roles,
        List<Vinculo> vinculos,
        Timestamp ultimoAcceso) {

    public record Rol(String codigo, String nombre) {
    }

    public record Vinculo(String tipo, Long personaId, String nombre, String detalle) {
    }
}
