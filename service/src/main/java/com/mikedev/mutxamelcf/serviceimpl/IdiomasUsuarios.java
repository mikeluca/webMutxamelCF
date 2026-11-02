package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.IdiomaApp;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.service.ComunicacionService;

/** El idioma de las cuentas de la app, para generar cada aviso en el idioma de quien lo recibe. */
@Component
public class IdiomasUsuarios {

    private final UsuarioAppDao usuarioAppDao;

    public IdiomasUsuarios(UsuarioAppDao usuarioAppDao) {
        this.usuarioAppDao = usuarioAppDao;
    }

    public String idiomaDe(Long usuarioId) {
        return usuarioId == null ? IdiomaApp.ES : IdiomaApp.normalizar(usuarioAppDao.obtenerIdioma(usuarioId));
    }

    /**
     * Los destinatarios agrupados por idioma, en el orden castellano, valenciano, inglés y sin grupos vacíos. Una
     * cuenta de la que no se sabe el idioma va con el castellano. Se conserva el orden de entrada dentro de cada grupo.
     */
    public Map<String, List<Long>> agrupar(Collection<Long> usuarioIds) {

        Map<String, List<Long>> grupos = new LinkedHashMap<>();

        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return grupos;
        }

        List<Long> ids = usuarioIds.stream().filter(id -> id != null).distinct().toList();

        if (ids.isEmpty()) {
            return grupos;
        }

        Map<Long, String> idiomas = usuarioAppDao.obtenerIdiomas(ids);
        Map<String, List<Long>> porIdioma = new LinkedHashMap<>();

        for (String idioma : IdiomaApp.TODOS) {
            porIdioma.put(idioma, new ArrayList<>());
        }

        ids.forEach(id -> porIdioma.get(IdiomaApp.normalizar(idiomas.get(id))).add(id));

        porIdioma.forEach((idioma, delIdioma) -> {
            if (!delIdioma.isEmpty()) {
                grupos.put(idioma, delIdioma);
            }
        });

        return grupos;
    }

    /** Llama a {@code accion(idioma, destinatarios)} una vez por cada idioma que tenga destinatarios. */
    public void porIdioma(Collection<Long> usuarioIds, BiConsumer<String, List<Long>> accion) {
        agrupar(usuarioIds).forEach(accion);
    }

    /**
     * Crea una comunicación privada por cada idioma con destinatarios, con el texto que genera {@code generador} para
     * ese idioma. {@code datosPush} se envía tal cual en cada una (el "tipo", la "pantalla" y el resto no cambian).
     */
    public void crearPrivadaPorIdioma(ComunicacionService comunicacionService, Collection<Long> destinatarios,
            Long autorId, Map<String, String> datosPush, Function<String, TextoAviso> generador) {

        porIdioma(destinatarios, (idioma, ids) -> {

            TextoAviso texto = generador.apply(idioma);

            Comunicacion comunicacion = new Comunicacion();
            comunicacion.setTitulo(texto.titulo());
            comunicacion.setContenido(texto.contenido());

            if (texto.contenidoPush() == null && datosPush == null) {
                comunicacionService.crearPrivada(comunicacion, ids, autorId);
            } else {
                comunicacionService.crearPrivada(comunicacion, ids, autorId, new OpcionesPushComunicacion(
                        texto.contenidoPush(), datosPush == null ? Map.of() : datosPush));
            }
        });
    }
}
