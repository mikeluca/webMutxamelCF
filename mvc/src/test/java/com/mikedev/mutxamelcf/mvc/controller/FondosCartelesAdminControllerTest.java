package com.mikedev.mutxamelcf.mvc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.dao.ImagenRedesFondoDao;
import com.mikedev.mutxamelcf.model.FondoCartel;

/** Pantalla "Fondos de carteles" con la seguridad real y el servicio real (solo el DAO es simulado). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FondosCartelesAdminControllerTest {

    private static final String BASE = "/admin/fondos-carteles";

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private ImagenRedesFondoDao fondoDao;

    @Autowired
    private MockMvc mockMvc;

    private static byte[] imagen(int ancho, int alto, String formato) throws Exception {

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), formato, salida);

        return salida.toByteArray();
    }

    private static MockMultipartFile archivo(byte[] bytes, String nombre, String tipo) {
        return new MockMultipartFile("archivo", nombre, tipo, bytes);
    }

    // ---- subir ---------------------------------------------------------------------------------------------

    @Test
    void unPngValidoSeGuardaConSuAnchoYAltoYElNombreDeQuienLoSube() throws Exception {

        byte[] png = imagen(1200, 1500, "png");

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir").file(archivo(png, "fondo.png", "image/png"))
                .with(csrf()).with(user("admin@club.es").roles("ADMIN"))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BASE)).andExpect(flash().attribute("mensaje", "Fondo de Primer Equipo guardado"));

        verify(fondoDao).guardar("PRIMER_EQUIPO", png, "image/png", 1200, 1500, "admin@club.es");
    }

    @Test
    void unJpgValidoTambienYElRolSuperPuede() throws Exception {

        byte[] jpg = imagen(1080, 1350, "jpg");

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir").file(archivo(jpg, "fondo.jpg", "image/jpeg"))
                .with(csrf()).with(user("super").roles("SUPER"))).andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("mensaje", "Fondo de Primer Equipo guardado"));

        verify(fondoDao).guardar("PRIMER_EQUIPO", jpg, "image/jpeg", 1080, 1350, "super");
    }

    @Test
    void demasiadoPequenaDaUnMensajeClaroYNoGuarda() throws Exception {

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir")
                .file(archivo(imagen(800, 1000, "png"), "pequena.png", "image/png")).with(csrf())
                .with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("error", containsString("demasiado pequeña")))
                .andExpect(flash().attribute("error", containsString("800×1000")))
                .andExpect(flash().attribute("error", containsString("1080×1350")));

        verify(fondoDao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void demasiadoGrandeDeMasDeCincoMegasDaUnMensajeClaroYNoGuarda() throws Exception {

        // 5 MB + 1 byte: dentro del límite de Spring (6 MB) pero por encima del nuestro.
        byte[] enorme = new byte[5 * 1024 * 1024 + 1];

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir").file(archivo(enorme, "enorme.png", "image/png"))
                .with(csrf()).with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("error", "La imagen pesa más de 5 MB"));

        verify(fondoDao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }


    @Test
    void unArchivoMayorQueElLimiteDeSpringVuelveALaPantallaConElMismoMensaje() {

        var advice = new SubidaDemasiadoGrandeAdvice();
        var redirect = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        var peticion = new org.springframework.mock.web.MockHttpServletRequest("POST",
                BASE + "/PRIMER_EQUIPO/subir");

        Object respuesta = advice.demasiadoGrande(peticion, redirect);

        org.assertj.core.api.Assertions.assertThat(respuesta).isEqualTo("redirect:/admin/fondos-carteles");
        org.assertj.core.api.Assertions.assertThat(redirect.getFlashAttributes().get("error"))
                .isEqualTo("La imagen pesa más de 5 MB");

        // En el resto de pantallas sigue siendo el 413 de siempre.
        var otra = new org.springframework.mock.web.MockHttpServletRequest("POST", "/admin/escudos-rivales/1/escudo");

        org.assertj.core.api.Assertions.assertThat((Object) advice.demasiadoGrande(otra, redirect))
                .isEqualTo((Object) org.springframework.http.ResponseEntity.status(413).build());
    }

    @Test
    void unTipoDeArchivoNoValidoSeRechaza() throws Exception {

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir")
                .file(archivo(imagen(1200, 1500, "gif"), "fondo.gif", "image/gif")).with(csrf())
                .with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("error", "Solo se admiten imágenes JPG o PNG"));

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir")
                .file(archivo("no soy una imagen".getBytes(), "fondo.png", "image/png")).with(csrf())
                .with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("error", containsString("no es una imagen válida")));

        verify(fondoDao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void sinArchivoYUnCartelQueNoExisteDanMensajeDeError() throws Exception {

        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir").file(archivo(new byte[0], "vacio.png", "image/png"))
                .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("error", "Selecciona la imagen del fondo (JPG o PNG)"));

        mockMvc.perform(multipart(BASE + "/NOEXISTE/subir")
                .file(archivo(imagen(1200, 1500, "png"), "fondo.png", "image/png")).with(csrf())
                .with(user("admin").roles("ADMIN"))).andExpect(flash().attribute("error", "El cartel no existe"));

        verify(fondoDao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }

    // ---- restaurar -----------------------------------------------------------------------------------------

    @Test
    void restaurarBorraLaFilaYVuelveElDePorDefecto() throws Exception {

        when(fondoDao.borrar("PRIMER_EQUIPO")).thenReturn(true);

        mockMvc.perform(post(BASE + "/PRIMER_EQUIPO/restaurar").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl(BASE))
                .andExpect(flash().attribute("mensaje", "Vuelve el fondo por defecto de Primer Equipo"));

        verify(fondoDao).borrar("PRIMER_EQUIPO");
    }

    // ---- pantalla y vista previa ----------------------------------------------------------------------------

    @Test
    void laPantallaMuestraLosDosCartelesConSuVistaPreviaYSuFormulario() throws Exception {

        mockMvc.perform(get(BASE).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Fondos de carteles")))
                .andExpect(content().string(containsString("Primer Equipo")))
                .andExpect(content().string(containsString("/admin/fondos-carteles/PRIMER_EQUIPO/imagen")))
                .andExpect(content().string(containsString("Restaurar el de por defecto")))
                .andExpect(content().string(containsString("Filial")))
                // El Filial funciona igual que el Primer Equipo: vista previa, subir y restaurar (ya no es "Próximamente")
                .andExpect(content().string(containsString("/admin/fondos-carteles/FILIAL/imagen")))
                .andExpect(content().string(containsString("/FILIAL/subir")))
                .andExpect(content().string(containsString("/FILIAL/restaurar")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Próximamente"))));
    }

    @Test
    void laVistaPreviaEsElFondoDeLaTablaOElDePorDefecto() throws Exception {

        mockMvc.perform(get(BASE + "/PRIMER_EQUIPO/imagen").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));

        byte[] png = imagen(1100, 1400, "png");
        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(
                new FondoCartel("PRIMER_EQUIPO", png, "image/png", 1100, 1400, LocalDateTime.now(), "admin"));

        mockMvc.perform(get(BASE + "/PRIMER_EQUIPO/imagen").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(png));

        // El Filial tiene su fondo por defecto (redes/cartel-filial/fondo.jpg)
        mockMvc.perform(get(BASE + "/FILIAL/imagen").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));

        mockMvc.perform(get(BASE + "/NOEXISTE/imagen").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void elMenuDelPanelTieneLaEntradaParaSuperYAdmin() throws Exception {

        mockMvc.perform(get(BASE).with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("href=\"/admin/fondos-carteles\"")));
        mockMvc.perform(get(BASE).with(user("super").roles("SUPER")))
                .andExpect(content().string(containsString("href=\"/admin/fondos-carteles\"")));
    }

    // ---- seguridad ------------------------------------------------------------------------------------------

    @Test
    void entrenadorRecibe403EnTodasLasRutasYNadaSeEjecuta() throws Exception {

        var entrenador = user("entrenador").roles("ENTRENADOR");

        mockMvc.perform(get(BASE).with(entrenador)).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/PRIMER_EQUIPO/imagen").with(entrenador)).andExpect(status().isForbidden());
        mockMvc.perform(multipart(BASE + "/PRIMER_EQUIPO/subir")
                .file(archivo(new byte[] { 1 }, "f.png", "image/png")).with(csrf()).with(entrenador))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/PRIMER_EQUIPO/restaurar").with(csrf()).with(entrenador))
                .andExpect(status().isForbidden());

        verify(fondoDao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
        verify(fondoDao, never()).borrar(eq("PRIMER_EQUIPO"));
    }

    @Test
    void sinSesionSeRedirigeAlLoginYSinCsrfSeRechazaAunSiendoAdmin() throws Exception {

        mockMvc.perform(get(BASE)).andExpect(status().is3xxRedirection());
        mockMvc.perform(post(BASE + "/PRIMER_EQUIPO/restaurar").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(fondoDao, never()).borrar(anyString());
    }
}
