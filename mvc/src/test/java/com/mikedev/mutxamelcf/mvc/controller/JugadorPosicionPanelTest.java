package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;

import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorForm;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

/** La posición y el dorsal en la ficha del jugador del panel. */
class JugadorPosicionPanelTest {

    private static String plantilla() throws IOException {
        return new ClassPathResource("templates/admin/jugadores.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void elPanelOfreceLasPosicionesDeLaListaEnElModelo() {

        JugadorService jugadores = mock(JugadorService.class);
        EquipoService equipos = mock(EquipoService.class);
        when(equipos.obtenerTodos()).thenReturn(List.of());
        when(jugadores.obtenerActivos()).thenReturn(List.of());
        JugadorAdminController controller = new JugadorAdminController(jugadores, mock(FamiliarService.class), equipos);

        Model model = new ExtendedModelMap();
        controller.listarJugadores("activos", model);

        assertThat(model.getAttribute("posiciones"))
                .isEqualTo(List.of("Portero", "Defensa", "Centrocampista", "Extremo", "Delantero"));
    }

    @Test
    void laPosicionEsUnDesplegableConLaListaYUnaOpcionVacia() throws IOException {

        String html = plantilla();

        assertThat(html).contains("<select class=\"form-control\" id=\"posicion\" name=\"posicion\">");
        assertThat(html).doesNotContain("<input type=\"text\" class=\"form-control\" id=\"posicion\"");
        assertThat(html).contains("<option value=\"\">(sin posición)</option>");
        assertThat(html).contains("th:each=\"posicion : ${posiciones}\"");
    }

    @Test
    void unTextoAntiguoSeMuestraComoLaOpcionTextoActualParaNoPerderlo() throws IOException {

        String html = plantilla();

        // Al editar se llama a seleccionarPosicion(texto): si no está en la lista, se añade "(texto actual)" seleccionada.
        assertThat(html).contains("seleccionarPosicion(data.posicion);");
        assertThat(html).contains("function seleccionarPosicion(texto)");
        assertThat(html).contains("opcion.value = valor;");
        assertThat(html).contains("(texto actual)");
        assertThat(html).contains("select.value = valor;");
        // Y al dar de alta uno nuevo se limpia (sin quedarse con el texto de otro jugador)
        assertThat(html).contains("seleccionarPosicion('');");
    }

    @Test
    void unDorsalRepetidoSeAvisaConUn409ConElMensajeClaro() {

        JugadorService jugadores = mock(JugadorService.class);
        EquipoService equipos = mock(EquipoService.class);
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(1L);
        equipo.setNombre("Cadete A");
        when(equipos.obtenerEquipoPorId(1L)).thenReturn(equipo);
        when(jugadores.guardarJugador(any()))
                .thenThrow(new DorsalRepetidoException("El dorsal 7 ya lo tiene Pablo Garcia"));
        JugadorAdminController controller = new JugadorAdminController(jugadores, mock(FamiliarService.class), equipos);

        JugadorForm form = new JugadorForm();
        form.setNombre("Luis");
        form.setEquipo(1L);
        form.setDorsal(7);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));

        ResponseEntity<Map<String, String>> respuesta = controller.guardarJugador(form,
                new BeanPropertyBindingResult(form, "jugadorForm"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).containsEntry("error", "El dorsal 7 ya lo tiene Pablo Garcia");
    }
}
