package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Caducada;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Coincidencia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Mia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.model.UsuarioApp;

/**
 * Las familias piden acceso a la información de sus hijos desde la app ("Soy familiar de un jugador") y el club lo
 * aprueba (docs/api-app-solicitudes-familia.md).
 */
public interface SolicitudFamiliaService {

    /** De 1 a 5 hijos por solicitud. */
    int MAX_HIJOS = 5;

    /** Una solicitud sin revisar, o rechazada, se borra a los 7 días. */
    int DIAS_HASTA_CADUCAR = 7;

    /** El recordatorio diario a coordinación sale con solicitudes pendientes de más de 2 días. */
    int DIAS_HASTA_RECORDATORIO = 2;

    /** De cuándo es la revisión: quien revisa desde la app (su cuenta) o desde el panel web (su usuario). */
    record Revisor(Long usuarioAppId, String usuarioPanel) {

        public static Revisor app(long usuarioAppId) {
            return new Revisor(usuarioAppId, null);
        }

        public static Revisor panel(String usuario) {
            return new Revisor(null, usuario);
        }

        public boolean esPanel() {
            return usuarioAppId == null;
        }
    }

    /** Qué ha pasado con una petición pública; las dos últimas no revelan nada más de la cuenta. */
    enum Resultado {
        /** Cuenta nueva (o solicitud repetida antes de confirmar el email): hay que mandar el código. */
        CODIGO_ENVIADO,
        /** El email ya tiene una cuenta: que inicie sesión y pida el acceso desde dentro. */
        CUENTA_EXISTENTE,
        /** El email tiene una invitación del club sin canjear: que use "Activar cuenta" con el código recibido. */
        INVITACION_PENDIENTE
    }

    /** El resultado y, si hay que mandar el código por email, a quién y cuál. */
    record Registro(Resultado resultado, String email, String nombre, String codigo, String idioma) {
    }

    // ---- familiar, sin sesión ---------------------------------------------------------------------------------------

    /** Valida la petición y crea la cuenta sin roles y la solicitud PENDIENTE_EMAIL (IllegalArgumentException si no vale). */
    Registro registrarPublica(SolicitudFamiliaRequest request);

    /** Un código nuevo para quien está confirmando su email; sin solicitud pendiente de confirmar, {@code null}. */
    Registro reenviarCodigo(String email);

    /**
     * Confirma el email con el código y fija la contraseña (las mismas reglas que "Activar cuenta"): la solicitud pasa a
     * PENDIENTE y se avisa a quien revisa. Un código malo gasta intentos de la cuenta y lanza
     * IllegalArgumentException con el mismo mensaje que la activación.
     */
    UsuarioApp verificarEmail(String email, String codigo, String password);

    /**
     * La cuenta acaba de activarse por la vía de "Activar cuenta": si tenía una solicitud PENDIENTE_EMAIL, pasa a
     * PENDIENTE (el código era el de la solicitud).
     */
    void confirmarSiProcede(int usuarioAppId);

    // ---- familiar, con sesión ---------------------------------------------------------------------------------------

    /** La misma solicitud para una cuenta que ya existe: sin crear cuenta ni verificar email; queda PENDIENTE. */
    Mia solicitarConSesion(int usuarioAppId, SolicitudFamiliaRequest request);

    /** Las solicitudes de la cuenta que no han caducado, la más reciente primero. Nunca lleva jugadores. */
    List<Mia> misSolicitudes(int usuarioAppId);

    // ---- quien revisa -----------------------------------------------------------------------------------------------

    /** Las pendientes que puede revisar, con sus coincidencias (un entrenador, solo las de sus equipos). */
    List<Revision> pendientes(Revisor revisor);

    int contarPendientes(Revisor revisor);

    /** Las revisadas en los últimos 7 días (solo el panel web). */
    List<Revision> historico(Revisor revisor);

    /** Jugadores activos por nombre entre los que puede ver quien revisa (mínimo 3 letras, máximo 20). */
    List<Coincidencia> buscarJugadores(Revisor revisor, String texto);

    /**
     * Aprueba un hijo con el jugador elegido: crea o reutiliza la ficha de familiar, la vincula al jugador y a la cuenta y
     * da el rol FAMILIAR. Avisa al familiar. SecurityException si no puede revisarlo.
     */
    Revision aprobarHijo(Revisor revisor, long hijoId, long jugadorId);

    /** Rechaza un hijo (motivo opcional) y avisa al familiar. SecurityException si no puede revisarlo. */
    Revision rechazarHijo(Revisor revisor, long hijoId, String motivo);

    // ---- tareas diarias ---------------------------------------------------------------------------------------------

    /**
     * Borra los datos de las solicitudes sin revisar o rechazadas de más de 7 días y las deja CADUCADAS; elimina la cuenta
     * que creó el registro si no tiene roles ni vínculos (las cuentas con roles no se tocan). Devuelve a quién avisar
     * por email de que ha caducado.
     */
    List<Caducada> caducarVencidas();

    /** Recordatorio a coordinación si hay solicitudes pendientes de más de 2 días. Devuelve a cuántas cuentas avisó. */
    int recordarPendientes();
}
