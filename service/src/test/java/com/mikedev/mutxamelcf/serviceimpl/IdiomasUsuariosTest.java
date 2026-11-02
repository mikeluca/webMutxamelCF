package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.service.ComunicacionService;

/** Los destinatarios de un aviso se agrupan por idioma y cada grupo recibe una sola comunicación en su idioma. */
class IdiomasUsuariosTest {

    private final ComunicacionService comunicacionService = mock(ComunicacionService.class);

    private IdiomasUsuarios idiomas(Map<Long, String> porUsuario) {
        return AvisosTest.idiomas(porUsuario);
    }

    @Test
    void agrupaPorIdiomaEnOrdenCastellanoValencianoIngles() {
        IdiomasUsuarios idiomas = idiomas(Map.of(10L, "en", 11L, "ca", 12L, "es", 13L, "en", 14L, "ca"));

        Map<String, List<Long>> grupos = idiomas.agrupar(List.of(10L, 11L, 12L, 13L, 14L));

        assertThat(grupos.keySet()).containsExactly("es", "ca", "en");
        assertThat(grupos.get("es")).containsExactly(12L);
        assertThat(grupos.get("ca")).containsExactly(11L, 14L);
        assertThat(grupos.get("en")).containsExactly(10L, 13L);
    }

    @Test
    void noCreaGruposVaciosNiRepiteNiAceptaNulos() {
        IdiomasUsuarios idiomas = idiomas(Map.of(10L, "en"));

        Map<String, List<Long>> grupos = idiomas.agrupar(Arrays.asList(10L, 10L, null, 20L));

        assertThat(grupos.keySet()).containsExactly("es", "en");
        assertThat(grupos.get("es")).containsExactly(20L);
        assertThat(grupos.get("en")).containsExactly(10L);
        assertThat(idiomas.agrupar(List.of())).isEmpty();
        assertThat(idiomas.agrupar(null)).isEmpty();
    }

    @Test
    void creaUnaComunicacionPorIdiomaConSuTextoYElMismoData() {
        IdiomasUsuarios idiomas = idiomas(Map.of(11L, "ca", 12L, "en", 13L, "en"));
        Map<String, String> data = Map.of("tipo", "X", "pantalla", "Y");

        idiomas.crearPrivadaPorIdioma(comunicacionService, List.of(10L, 11L, 12L, 13L), 99L, data,
                idioma -> new TextoAviso("T-" + idioma, "C-" + idioma, "P-" + idioma));

        ArgumentCaptor<Comunicacion> comunicaciones = ArgumentCaptor.forClass(Comunicacion.class);
        ArgumentCaptor<List<Long>> destinatarios = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<OpcionesPushComunicacion> opciones = ArgumentCaptor.forClass(OpcionesPushComunicacion.class);

        verify(comunicacionService, times(3)).crearPrivada(comunicaciones.capture(), destinatarios.capture(),
                eq(99L), opciones.capture());

        assertThat(comunicaciones.getAllValues()).extracting(Comunicacion::getTitulo)
                .containsExactly("T-es", "T-ca", "T-en");
        assertThat(comunicaciones.getAllValues()).extracting(Comunicacion::getContenido)
                .containsExactly("C-es", "C-ca", "C-en");
        assertThat(destinatarios.getAllValues()).containsExactly(List.of(10L), List.of(11L), List.of(12L, 13L));
        assertThat(opciones.getAllValues()).extracting(OpcionesPushComunicacion::contenidoPush)
                .containsExactly("P-es", "P-ca", "P-en");
        assertThat(opciones.getAllValues()).allSatisfy(o -> assertThat(o.datos()).isEqualTo(data));
    }

    @Test
    void sinTextoDePushNiDataLlamaALaVersionSimple() {
        IdiomasUsuarios idiomas = idiomas(Map.of(11L, "ca"));

        idiomas.crearPrivadaPorIdioma(comunicacionService, List.of(10L, 11L), 99L, null,
                idioma -> new TextoAviso("T-" + idioma, "C-" + idioma));

        verify(comunicacionService, times(2)).crearPrivada(any(Comunicacion.class), any(), eq(99L));
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(),
                any(OpcionesPushComunicacion.class));
    }

    @Test
    void sinDestinatariosNoCreaNada() {
        idiomas(Map.of()).crearPrivadaPorIdioma(comunicacionService, List.of(), 99L, null,
                idioma -> new TextoAviso("T", "C"));

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }
}
