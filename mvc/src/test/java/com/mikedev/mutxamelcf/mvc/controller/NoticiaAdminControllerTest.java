package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.NoticiaForm;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticiaAdminControllerTest {

    private NoticiaService noticiaService;
    private EquipoService equiposService;
    private NotificacionAppService notificacionAppService;

    private NoticiaAdminController controller;

    @BeforeEach
    void setUp() {
        noticiaService = mock(NoticiaService.class);
        equiposService = mock(EquipoService.class);
        notificacionAppService = mock(NotificacionAppService.class);

        controller = new NoticiaAdminController(noticiaService, equiposService, notificacionAppService);
    }

    @Test
    void guardarNoticiaEscapaElTituloParaEvitarXss() {
        NoticiaForm form = new NoticiaForm();
        form.setTitulo("<script>alert('xss')</script>");
        form.setContenido("contenido normal");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));

        when(noticiaService.guardarNoticia(any(NoticiaDTO.class))).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        org.mockito.ArgumentCaptor<NoticiaDTO> captor = org.mockito.ArgumentCaptor.forClass(NoticiaDTO.class);
        verify(noticiaService).guardarNoticia(captor.capture());

        String tituloGuardado = captor.getValue().getTitulo();
        assertFalse(tituloGuardado.contains("<script>"), "El titulo no debe contener HTML sin escapar");
        assertTrue(tituloGuardado.contains("&lt;script&gt;"), "El titulo debe llevar las entidades HTML escapadas");
    }
}
