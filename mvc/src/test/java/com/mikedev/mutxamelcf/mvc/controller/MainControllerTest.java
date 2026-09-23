package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.ResultadoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class MainControllerTest {

    private MainController controller;

    @BeforeEach
    void setUp() {
        controller = new MainController(
                mock(ComunicacionesService.class),
                mock(JugadorService.class),
                mock(CuerpoTecnicoService.class),
                mock(NoticiaService.class),
                mock(ResultadoService.class),
                mock(EquipoService.class));
    }

    @Test
    void sinParametroErrorNoHayMensaje() {
        Model model = new ExtendedModelMap();

        String vista = controller.login(null, model);

        assertEquals("login", vista);
        assertNull(model.getAttribute("errorMessage"));
    }

    @Test
    void errorTrueMuestraCredencialesIncorrectas() {
        Model model = new ExtendedModelMap();

        controller.login("true", model);

        assertEquals("Usuario o contraseña incorrectos", model.getAttribute("errorMessage"));
    }

    @Test
    void errorBloqueadoMuestraMensajeDeBloqueoPorRateLimit() {
        Model model = new ExtendedModelMap();

        controller.login("bloqueado", model);

        assertEquals(
                "Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos.",
                model.getAttribute("errorMessage"));
    }
}
