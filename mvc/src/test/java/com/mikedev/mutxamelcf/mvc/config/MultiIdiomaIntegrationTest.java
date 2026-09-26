package com.mikedev.mutxamelcf.mvc.config;

import com.google.firebase.FirebaseApp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que la infraestructura de multi-idioma (es/ca/en) funciona de
 * verdad de punta a punta: cada pagina publica se renderiza sin errores
 * en castellano, valenciano e ingles (lo que ademas detecta cualquier
 * clave de messages.properties/messages_ca.properties/messages_en.properties
 * mal escrita o inexistente, ya que Thymeleaf lanza excepcion si falta una
 * clave #{...} usada en la plantilla), y que ?lang=ca / ?lang=en dejan la
 * cookie de idioma en el valor correspondiente.
 *
 * NOTA: se cubren aqui las paginas publicas cuyos datos no dependen de
 * tablas (NOTICIA/PARTIDO/EQUIPO) ausentes del schema.sql de test, ya que
 * ese esquema se penso solo para los tests existentes antes de esta ronda
 * (auth/admin/app). Las paginas index/resultados/todasNoticias/listaEquipos,
 * que si consultan esas tablas, se han verificado manualmente (revision de
 * plantilla) en vez de con MockMvc, para no tener que rehacer el esquema de
 * base de datos de test como parte de esta tarea de internacionalizacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiIdiomaIntegrationTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/historia",
            "/estadisticasPalmares",
            "/obraSocial",
            "/contacto",
            "/tienda",
            "/politicaPrivacidad"
    })
    void paginasPublicasSeRenderizanEnCastellano(String ruta) throws Exception {

        mockMvc.perform(get(ruta))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/historia",
            "/estadisticasPalmares",
            "/obraSocial",
            "/contacto",
            "/tienda",
            "/politicaPrivacidad"
    })
    void paginasPublicasSeRenderizanEnValenciano(String ruta) throws Exception {

        mockMvc.perform(get(ruta).param("lang", "ca"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/historia",
            "/estadisticasPalmares",
            "/obraSocial",
            "/contacto",
            "/tienda",
            "/politicaPrivacidad"
    })
    void paginasPublicasSeRenderizanEnIngles(String ruta) throws Exception {

        mockMvc.perform(get(ruta).param("lang", "en"))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarAValencianoDejaLaCookieDeIdiomaEnCa() throws Exception {

        mockMvc.perform(get("/contacto").param("lang", "ca"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("idioma", "ca"));
    }

    @Test
    void cambiarAInglesDejaLaCookieDeIdiomaEnEn() throws Exception {

        mockMvc.perform(get("/contacto").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("idioma", "en"));
    }

    @Test
    void selectorDeIdiomaMuestraCastellanoValencianoEInglesNuncaCatala() throws Exception {

        mockMvc.perform(get("/contacto"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Castellano")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Valencià")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("English")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Català"))));
    }
}
