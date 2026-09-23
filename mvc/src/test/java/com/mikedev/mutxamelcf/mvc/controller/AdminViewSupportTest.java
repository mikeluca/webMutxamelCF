package com.mikedev.mutxamelcf.mvc.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class AdminViewSupportTest {

    @Test
    void validarImagenAceptaUnaImagenValida() {
        MockMultipartFile imagen = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[100]);

        assertThat(AdminViewSupport.validarImagen(imagen, 1024)).isNull();
    }

    @Test
    void validarImagenRechazaUnFicheroDemasiadoGrande() {
        MockMultipartFile imagen = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[2000]);

        assertThat(AdminViewSupport.validarImagen(imagen, 1024)).contains("tamaño máximo");
    }

    @Test
    void validarImagenRechazaUnTipoNoPermitido() {
        MockMultipartFile imagen = new MockMultipartFile("foto", "foto.pdf", "application/pdf", new byte[100]);

        assertThat(AdminViewSupport.validarImagen(imagen, 1024)).contains("JPEG, PNG o WEBP");
    }

    @Test
    void validarImagenRechazaUnContentTypeNulo() {
        MockMultipartFile imagen = new MockMultipartFile("foto", "foto", null, new byte[100]);

        assertThat(AdminViewSupport.validarImagen(imagen, 1024)).contains("JPEG, PNG o WEBP");
    }
}
