package com.mikedev.mutxamelcf.service;

/**
 * Avisos en directo del partido del primer equipo (alineación, inicio,
 * goles, descanso, segunda parte, final), reservados al rol
 * RETRANSMISION. Cada acción compone un mensaje ya con el resultado
 * incluido y lo manda por push a todas las cuentas de la app.
 */
public interface PartidoEnVivoService {

    void enviarAlineacion(Long usuarioId, String onceInicial, String suplentes);

    void enviarInicioPartido(Long usuarioId);

    void enviarGolFavor(Long usuarioId, String autor);

    void enviarGolContra(Long usuarioId);

    void enviarDescanso(Long usuarioId);

    void enviarSegundaParte(Long usuarioId);

    void enviarFinalPartido(Long usuarioId);
}
