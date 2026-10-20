package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;

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
    private final OpcionesPushComunicacion opcionesPush;

    public ComunicacionPushEvent(Long comunicacionId, String titulo, String contenido, Set<Long> usuarioIds,
            Long autorIdParaChatPrivado) {
        this(comunicacionId, titulo, contenido, usuarioIds, autorIdParaChatPrivado, null);
    }

    public ComunicacionPushEvent(Long comunicacionId, String titulo, String contenido, Set<Long> usuarioIds,
            Long autorIdParaChatPrivado, OpcionesPushComunicacion opcionesPush) {
        this.comunicacionId = comunicacionId;
        this.titulo = titulo;
        this.contenido = contenido;
        this.usuarioIds = usuarioIds;
        this.autorIdParaChatPrivado = autorIdParaChatPrivado;
        this.opcionesPush = opcionesPush;
    }

    public Long getComunicacionId() {
        return comunicacionId;
    }

    public String getTitulo() {
        return titulo;
    }

    /** El contenido de la comunicación. */
    public String getContenido() {
        return contenido;
    }

    /** El texto del push: el de las opciones si lo traen; si no, el contenido. */
    public String getContenidoPush() {
        return opcionesPush != null && opcionesPush.contenidoPush() != null
                ? opcionesPush.contenidoPush()
                : contenido;
    }

    /** Datos extra del payload del push (puede incluir "tipo"). */
    public Map<String, String> getDatosExtra() {
        return opcionesPush != null ? opcionesPush.datos() : Map.of();
    }

    public List<Long> getUsuarioIds() {
        return List.copyOf(usuarioIds);
    }

    public Long getAutorIdParaChatPrivado() {
        return autorIdParaChatPrivado;
    }
}
