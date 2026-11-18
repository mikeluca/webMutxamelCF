package com.mikedev.mutxamelcf.mvc.config;


import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Una cuenta de la app SIN ningún rol (la de una familia que ha pedido acceso y el club aún no ha aprobado) solo puede
 * usar lo mínimo para ver el estado de su solicitud ({@link #RUTAS_SIN_ROL}). Todo lo demás de /api/app/** exige tener
 * al menos un rol: así esa cuenta no ve ningún dato de jugadores, equipos, convocatorias, calendario, comunicaciones...
 * aunque algún endpoint no compruebe él mismo a quién pertenece lo que devuelve (docs/api-app-solicitudes-familia.md).
 */
public final class AccesoCuentaSinRoles {

    /** Lo único de /api/app/** a lo que puede llegar una cuenta sin roles (con sesión). */
    public static final String[] RUTAS_SIN_ROL = {
            // Quién soy, mi perfil (vacío: sin vínculos) y eliminar mi cuenta.
            "/api/app/auth/me",
            "/api/app/auth/cuenta/**",
            "/api/app/perfil/**",
            // Pedir el acceso y ver el estado de mi solicitud (la revisión la rechaza el propio servicio).
            "/api/app/solicitudes-familia/**",
            // Recibir el aviso de la respuesta (push, bandeja) y elegir idioma y avisos.
            "/api/app/dispositivos/**",
            "/api/app/notificaciones/**",
            "/api/app/preferencias-notificacion/**",
            "/api/app/preferencias/idioma/**" };

    private AccesoCuentaSinRoles() {
    }

    /** Autenticado y con al menos un rol. */
    public static final AuthorizationManager<RequestAuthorizationContext> CON_ALGUN_ROL = (autenticacion,
            contexto) -> new AuthorizationDecision(tieneAlgunRol(autenticacion.get()));

    static boolean tieneAlgunRol(Authentication autenticacion) {
        return autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken)
                && !autenticacion.getAuthorities().isEmpty();
    }

}
