package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;
import java.util.Set;

/**
 * BE-02: se publica al final de {@link ComunicacionServiceImpl#crear}
 * (todavia dentro de la transaccion) para que el envio real de los push
 * -- una llamada de red por dispositivo -- ocurra despues de que la
 * transaccion confirme (ver {@link ComunicacionPushListener}), y nunca si
 * la transaccion acaba haciendo rollback.
 */
public class ComunicacionPushEvent {

    private final Long comunicacionId;
    private final String titulo;
    private final String contenido;
    private final Set<Long> usuarioIds;
    private final Long autorIdParaChatPrivado;

    public ComunicacionPushEvent(Long comunicacionId, String titulo, String contenido, Set<Long> usuarioIds,
            Long autorIdParaChatPrivado) {
        this.comunicacionId = comunicacionId;
        this.titulo = titulo;
        this.contenido = contenido;
        this.usuarioIds = usuarioIds;
        this.autorIdParaChatPrivado = autorIdParaChatPrivado;
    }

    public Long getComunicacionId() {
        return comunicacionId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getContenido() {
        return contenido;
    }

    public List<Long> getUsuarioIds() {
        return List.copyOf(usuarioIds);
    }

    public Long getAutorIdParaChatPrivado() {
        return autorIdParaChatPrivado;
    }
}
