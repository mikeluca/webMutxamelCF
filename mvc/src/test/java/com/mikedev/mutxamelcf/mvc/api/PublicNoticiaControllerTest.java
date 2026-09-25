package com.mikedev.mutxamelcf.mvc.api;

import com.mikedev.mutxamelcf.model.NoticiaAppDTO;
import com.mikedev.mutxamelcf.service.NoticiaService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicNoticiaControllerTest {

    @Test
    void obtenerNoticiasSinParametrosDelegaEnElServicioConLimiteNulo() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        when(service.obtenerNoticiasParaAppPagina(null, null)).thenReturn(List.of(new NoticiaAppDTO()));

        assertThat(controller.obtenerNoticias(null, null)).hasSize(1);
    }

    @Test
    void obtenerNoticiasConAntesIdYLimiteLosPasaAlServicio() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        when(service.obtenerNoticiasParaAppPagina(42, 10)).thenReturn(List.of());

        controller.obtenerNoticias(42, 10);

        org.mockito.Mockito.verify(service).obtenerNoticiasParaAppPagina(42, 10);
    }

    @Test
    void obtenerNoticiaDelegaEnElServicio() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        NoticiaAppDTO noticia = new NoticiaAppDTO();
        when(service.obtenerNoticiaParaApp(1)).thenReturn(noticia);

        assertThat(controller.obtenerNoticia(1)).isSameAs(noticia);
    }

    @Test
    void obtenerImagenDevuelve404SiNoHayImagen() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        when(service.obtenerImagenNoticia(1)).thenReturn(null);

        assertThat(controller.obtenerImagen(1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerImagenDevuelveLaImagenConContentTypeJpeg() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        when(service.obtenerImagenNoticia(1)).thenReturn(new byte[] { 1, 2, 3 });

        ResponseEntity<byte[]> response = controller.obtenerImagen(1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("image/jpeg");
    }

    @Test
    void obtenerImagenMiniDevuelve404SiLaImagenEstaVacia() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        when(service.obtenerImagenNoticiaMini(1)).thenReturn(new byte[0]);

        assertThat(controller.obtenerImagenMini(1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerImagenMiniDevuelveLaImagen() {
        NoticiaService service = mock(NoticiaService.class);
        PublicNoticiaController controller = new PublicNoticiaController(service);

        when(service.obtenerImagenNoticiaMini(1)).thenReturn(new byte[] { 1 });

        assertThat(controller.obtenerImagenMini(1).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
