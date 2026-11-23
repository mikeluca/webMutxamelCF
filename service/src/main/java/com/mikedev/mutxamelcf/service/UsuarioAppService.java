package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.PersonasVinculablesResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.UsuarioAppAdminResponse;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;

import java.util.List;

public interface UsuarioAppService {

    /**
     * Validez del código de activación de una cuenta (invitación del club, reenvío y registro de una familia): 5 días
     * desde que se envía el email. El único sitio donde se define; el texto de los emails usa {@code DIAS_VALIDEZ_ACTIVACION}.
     */
    int HORAS_VALIDEZ_ACTIVACION = 120;

    int DIAS_VALIDEZ_ACTIVACION = HORAS_VALIDEZ_ACTIVACION / 24;

    /** Validez del código de recuperación de contraseña. */
    int MINUTOS_VALIDEZ_RECUPERACION = 30;

    UsuarioApp obtenerPorEmail(String email);

    UsuarioApp obtenerPorId(int id);

    UsuarioApp obtenerPorTokenActivacion(String token);

    int crearUsuario(UsuarioApp usuario);

    String generarTokenActivacion(int usuarioId);

    UsuarioApp activarCuenta(String email, String codigo, String password);

    /**
     * Genera un código de recuperación de contraseña de 6 dígitos para una cuenta activa
     * y no eliminada (IllegalStateException si no lo es): guarda su hash con 30 minutos
     * de validez e intentos a 0, y devuelve el código en claro para enviarlo por email.
     */
    String generarCodigoRecuperacion(int usuarioId);

    /**
     * Cambia la contraseña con un código de recuperación. Valida la contraseña con las
     * mismas reglas que {@link #activarCuenta}; un código incorrecto, caducado o ausente
     * lanza IllegalArgumentException con el mismo mensaje (el incorrecto gasta un intento
     * y a los 5 el código se borra). Si todo va bien guarda el hash, borra los códigos
     * de recuperación y de activación y fija FECHA_CAMBIO_PASSWORD; devuelve la cuenta.
     */
    UsuarioApp restablecerPassword(String email, String codigo, String password);

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

    /**
     * Lo que ofrece el formulario de invitar de la app: personas sin cuenta y roles sin
     * persona. SecurityException si el solicitante no es ADMIN_APP.
     */
    com.mikedev.mutxamelcf.model.VinculosDisponiblesResponse obtenerVinculosDisponibles(int solicitanteId);

    /**
     * Invitar desde la app: comprueba que el solicitante es ADMIN_APP (SecurityException si no),
     * que no se asigna el rol ADMIN_APP (IllegalArgumentException) y delega en
     * {@link #invitarUsuario}, la misma lógica del panel web. Un email ya registrado lanza
     * {@link EmailYaRegistradoException}.
     */
    InvitacionUsuarioApp invitarUsuarioDesdeApp(
            int solicitanteId, com.mikedev.mutxamelcf.model.InvitacionAppRequest request);

    /**
     * Añade uno o varios vínculos/roles nuevos a una cuenta ya
     * existente (p. ej. a un jugador se le añade también el rol de
     * entrenador). El email de la cuenta no se toca.
     */
    void agregarVinculosAUsuarioExistente(int usuarioAppId, AnadirVinculosRequest request);

    /**
     * Quita un vínculo concreto de una cuenta. El rol asociado solo se
     * quita si no queda ningún otro vínculo del mismo tipo.
     */
    void quitarVinculo(int usuarioAppId, VinculoSolicitado vinculo);

    InvitacionUsuarioApp reenviarInvitacion(int usuarioAppId);

    /**
     * Cuentas con ACTIVO = 0, no eliminadas y nunca activadas (FECHA_ACTIVACION
     * null), de la más reciente a la más antigua.
     */
    java.util.List<com.mikedev.mutxamelcf.model.UsuarioPendienteResponse> listarUsuariosPendientes(int solicitanteId);

    /**
     * Comprueba que el solicitante es ADMIN_APP (SecurityException si no) y que la
     * cuenta sigue pendiente de activar (IllegalStateException si ya está activa, la
     * eliminó su titular o un administrador la desactivó) y reenvía la invitación con
     * {@link #reenviarInvitacion(int)}: código nuevo, 5 días (120 h) de validez y contador de
     * intentos a cero.
     */
    InvitacionUsuarioApp reenviarActivacionPendiente(int solicitanteId, int usuarioAppId);

    /** SecurityException si el usuario no tiene el rol ADMIN_APP. */
    void exigirAdminApp(int solicitanteId);

    /** Como {@link #exigirAdminApp(int)}, con el mensaje de la acción que se intentaba (p. ej. importar la FFCV). */
    void exigirAdminApp(int solicitanteId, String mensaje);

    /*
     * Administración de usuarios desde la app (ADMIN_APP). Envuelven la lógica del panel web y añaden las reglas de
     * seguridad: SecurityException si el solicitante no es ADMIN_APP; IllegalArgumentException si intenta desactivarse,
     * tocar la cuenta de otro ADMIN_APP (desactivarla, eliminarla, quitarle roles) o asignar el rol ADMIN_APP.
     */

    /** Listado de cuentas con filtros opcionales: q (nombre o email, sin acentos), rol (código o SIN_ROLES) y estado. */
    List<com.mikedev.mutxamelcf.model.UsuarioAdminApp> listarUsuariosDesdeApp(
            int solicitanteId, String q, String rol, String estado);

    com.mikedev.mutxamelcf.model.OpcionesUsuariosApp obtenerOpcionesUsuariosDesdeApp(int solicitanteId);

    void actualizarNombreDesdeApp(int solicitanteId, int usuarioAppId, String nombre, String apellidos);

    void agregarVinculosDesdeApp(int solicitanteId, int usuarioAppId, AnadirVinculosRequest request);

    void quitarVinculoDesdeApp(int solicitanteId, int usuarioAppId, VinculoSolicitado vinculo);

    void activarDesdeApp(int solicitanteId, int usuarioAppId);

    void desactivarDesdeApp(int solicitanteId, int usuarioAppId);

    void eliminarInvitacionDesdeApp(int solicitanteId, int usuarioAppId);

    /** Como el panel: reenvía la invitación a cualquier cuenta no activa (código nuevo; el email lo manda quien llama). */
    InvitacionUsuarioApp reenviarInvitacionDesdeApp(int solicitanteId, int usuarioAppId);

    void activarUsuarioAdmin(int usuarioAppId);

    /**
     * Como {@link #activarUsuarioAdmin(int)} pero con un UPDATE condicionado a ACTIVO = 0.
     * Devuelve false si la cuenta ya estaba activa (otro administrador se adelantó).
     */
    boolean activarUsuarioAdminSiInactivo(int usuarioAppId);

    void desactivarUsuarioAdmin(int usuarioAppId);

    void eliminarInvitacion(int usuarioAppId);

    /**
     * Nombre de pila (sin apellidos) de la persona vinculada a la cuenta (jugador,
     * familiar o cuerpo técnico), o {@code null} si no tiene ninguno.
     */
    String obtenerNombreDePila(int usuarioAppId);

    /** El idioma de la cuenta ("es", "ca" o "en"). */
    String obtenerIdioma(int usuarioAppId);

    /** Guarda el idioma de la cuenta; cualquier valor que no sea "es", "ca" o "en" se guarda como "es". */
    void actualizarIdioma(int usuarioAppId, String idioma);

    /**
     * Cambia el nombre propio de la cuenta (el que se muestra cuando no
     * tiene ficha vinculada). Sin ficha el nombre es obligatorio; los
     * apellidos son siempre opcionales.
     */
    void actualizarNombre(int usuarioAppId, String nombre, String apellidos);

    /**
     * Nombre completo para mostrar: el de la ficha vinculada si la hay y,
     * si no, el de la propia cuenta. {@code null} si no tiene ninguno.
     */
    String obtenerNombreMostrable(int usuarioAppId);
}