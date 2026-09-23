package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.EquipoDTO;
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
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void listarNoticiasRellenaElModelo() {
        when(equiposService.obtenerTodos()).thenReturn(List.of(new EquipoDTO()));
        when(noticiaService.obtenerTodas()).thenReturn(List.of(new NoticiaDTO()));
        when(equiposService.obtenerCategorias()).thenReturn(List.of("Senior"));

        Model model = new ExtendedModelMap();
        String vista = controller.listarNoticias(model);

        assertEquals("admin/noticias", vista);
        assertEquals(1, ((List<?>) model.getAttribute("listaNoticias")).size());
    }

    @Test
    void guardarNoticiaDevuelveBadRequestSiLaNoticiaAEditarNoExiste() {
        NoticiaForm form = new NoticiaForm();
        form.setId(99L);
        form.setTitulo("Titulo");
        form.setContenido("Contenido");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));
        when(noticiaService.obtenerNoticiaPorId(99)).thenReturn(null);

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La noticia que intentas editar ya no existe.", response.getBody().get("error"));
        verify(noticiaService, never()).guardarNoticia(any());
    }

    @Test
    void guardarNoticiaConvierteLosSaltosDeLineaEnBr() {
        NoticiaForm form = new NoticiaForm();
        form.setTitulo("Titulo");
        form.setContenido("Linea 1\nLinea 2");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));
        when(noticiaService.guardarNoticia(any())).thenReturn(true);

        controller.guardarNoticia(form);

        org.mockito.ArgumentCaptor<NoticiaDTO> captor = org.mockito.ArgumentCaptor.forClass(NoticiaDTO.class);
        verify(noticiaService).guardarNoticia(captor.capture());
        assertTrue(captor.getValue().getContenido().contains("<br>"));
    }

    @Test
    void guardarNoticiaDevuelveBadRequestSiLaImagenNoEsValida() {
        NoticiaForm form = new NoticiaForm();
        form.setTitulo("Titulo");
        form.setContenido("Contenido");
        byte[] imagenDemasiadoGrande = new byte[3 * 1024 * 1024];
        form.setImagen(new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", imagenDemasiadoGrande));

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(noticiaService, never()).guardarNoticia(any());
    }

    @Test
    void guardarNoticiaConservaLaImagenExistenteYFechaAlEditarSinNuevaImagen() {
        NoticiaForm form = new NoticiaForm();
        form.setId(5L);
        form.setTitulo("Titulo");
        form.setContenido("Contenido");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));

        NoticiaDTO existente = new NoticiaDTO();
        existente.setId(5);
        java.util.Date fechaOriginal = new java.util.Date(0);
        existente.setFecha(fechaOriginal);
        when(noticiaService.obtenerNoticiaPorId(5)).thenReturn(existente);
        when(noticiaService.obtenerImagenNoticia(5)).thenReturn(new byte[] { 1, 2, 3 });
        when(noticiaService.guardarNoticia(any())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Noticia actualizada correctamente.", response.getBody().get("mensaje"));
        org.mockito.ArgumentCaptor<NoticiaDTO> captor = org.mockito.ArgumentCaptor.forClass(NoticiaDTO.class);
        verify(noticiaService).guardarNoticia(captor.capture());
        assertEquals(3, captor.getValue().getImagen().length);
        assertEquals(fechaOriginal, captor.getValue().getFecha());
        verify(notificacionAppService, never()).difundirATodos(any(), any(), any(), any());
    }

    @Test
    void guardarNoticiaNuevaDifundeNotificacionATodos() {
        NoticiaForm form = new NoticiaForm();
        form.setTitulo("Titulo nuevo");
        form.setContenido("Contenido");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));
        when(noticiaService.guardarNoticia(any())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Noticia generada correctamente.", response.getBody().get("mensaje"));
        verify(notificacionAppService).difundirATodos(org.mockito.ArgumentMatchers.eq("NOTICIA"), any(), any(), any());
    }

    @Test
    void guardarNoticiaDevuelveBadRequestCuandoElServicioFalla() {
        NoticiaForm form = new NoticiaForm();
        form.setTitulo("Titulo");
        form.setContenido("Contenido");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));
        when(noticiaService.guardarNoticia(any())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error guardando la noticia.", response.getBody().get("error"));
    }

    @Test
    void guardarNoticiaDevuelve500SiElServicioLanzaExcepcionInesperada() {
        NoticiaForm form = new NoticiaForm();
        form.setTitulo("Titulo");
        form.setContenido("Contenido");
        form.setImagen(new MockMultipartFile("imagen", new byte[0]));
        when(noticiaService.guardarNoticia(any())).thenThrow(new RuntimeException("fallo"));

        ResponseEntity<Map<String, String>> response = controller.guardarNoticia(form);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void borrarNoticiaRedirigeALaListaDeNoticias() {
        assertEquals("redirect:/admin/noticias", controller.borrarNoticia(1));
        verify(noticiaService).eliminarNoticia(1);
    }

    @Test
    void borrarNoticiaRedirigeAunqueElServicioLanceExcepcion() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo")).when(noticiaService).eliminarNoticia(1);

        assertEquals("redirect:/admin/noticias", controller.borrarNoticia(1));
    }
}
