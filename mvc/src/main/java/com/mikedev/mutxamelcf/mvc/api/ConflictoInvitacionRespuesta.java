package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.mikedev.mutxamelcf.model.ConflictoInvitacionException;

/**
 * El 409 de un jugador ya autorizado como invitado ese día: un cuerpo con el código, para que la app no dependa del
 * texto del mensaje (mismo estilo que PRUEBAS_SIN_CONFIRMAR del reparto).
 */
public final class ConflictoInvitacionRespuesta {

    private ConflictoInvitacionRespuesta() {
    }

    public static ResponseEntity<Map<String, Object>> de(ConflictoInvitacionException e) {

        Map<String, Object> conflicto = new LinkedHashMap<>();

        conflicto.put("codigo", e.getCodigo());
        conflicto.put("mensaje", e.getMessage());
        conflicto.put("jugadorId", e.getJugadorId());
        conflicto.put("equipoDestinoId", e.getEquipoDestinoId());
        conflicto.put("equipoDestino", e.getEquipoDestino());
        conflicto.put("fecha", e.getFecha() != null ? e.getFecha().toString() : null);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicto);
    }
}
