package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.PersonasVinculablesResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.UsuarioAppAdminResponse;

import java.util.List;

public interface UsuarioAppService {

    UsuarioApp obtenerPorEmail(String email);

    UsuarioApp obtenerPorId(int id);

    UsuarioApp obtenerPorTokenActivacion(String token);

    int crearUsuario(UsuarioApp usuario);

    String generarTokenActivacion(int usuarioId);

    UsuarioApp activarCuenta(String token, String password);

    LoginAppResponse login(String email, String password);

    void actualizarUltimoAcceso(int id);

    List<RolApp> obtenerRoles(int usuarioAppId);

    RolApp obtenerRolPorCodigo(String codigo);

    boolean tieneRol(int usuarioAppId, String codigoRol);

    void asignarRol(int usuarioAppId, int rolId);

    void eliminarRol(int usuarioAppId, int rolId);

    /*
     * Administración desde la web (panel SUPER).
     */

    List<UsuarioAppAdminResponse> listarUsuariosAdmin();

    PersonasVinculablesResponse obtenerPersonasVinculables();

    InvitacionUsuarioApp invitarUsuario(InvitarUsuarioAppRequest request);

    InvitacionUsuarioApp reenviarInvitacion(int usuarioAppId);

    void activarUsuarioAdmin(int usuarioAppId);

    void desactivarUsuarioAdmin(int usuarioAppId);

    void eliminarInvitacion(int usuarioAppId);
}