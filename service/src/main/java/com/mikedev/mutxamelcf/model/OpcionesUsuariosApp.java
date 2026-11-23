package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Lo que necesita la app para invitar y editar cuentas (GET /api/app/admin/usuarios/opciones): los roles sin persona
 * que se pueden asignar (nunca ADMIN_APP: solo se asigna desde el panel web) y las personas sin cuenta por tipo.
 */
public record OpcionesUsuariosApp(
        List<VinculosDisponiblesResponse.Rol> roles,
        PersonasVinculablesResponse personas) {
}
