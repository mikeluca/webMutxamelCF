package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.mvc.web.Patrocinador;
import com.mikedev.mutxamelcf.mvc.web.PatrocinadoresClub;

/** La lista única de patrocinadores: endpoint público para la app y pie de página de la web. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPatrocinadorControllerTest {

    private static final List<String> NOMBRES = List.of("Frutas y verduras Juanete", "Unlimited Developments",
            "Ayto Mutxamel", "La bodega de Elias", "Aresala", "AA Energy", "Dental Company Mutxamel");

    @MockBean
    private FirebaseApp firebaseApp;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void devuelveLosSieteEnOrdenSinSesionYConCacheDeUnaHora() throws Exception {

        var resultado = mockMvc.perform(get("/api/public/patrocinadores")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7)).andExpect(jsonPath("$[0].nombre").value(NOMBRES.get(0)))
                .andExpect(jsonPath("$[0].imagenUrl").value("/images/frutasJuanete.jpg"))
                .andExpect(jsonPath("$[0].url").value("https://www.instagram.com/frutasjuanete/"))
                .andExpect(jsonPath("$[0].circular").value(false)).andReturn();

        assertThat(resultado.getResponse().getHeader("Cache-Control")).contains("public").contains("max-age=3600");
        assertThat(resultado.getResponse().getContentType()).startsWith("application/json");

        for (int i = 0; i < NOMBRES.size(); i++) {
            mockMvc.perform(get("/api/public/patrocinadores"))
                    .andExpect(jsonPath("$[" + i + "].nombre").value(NOMBRES.get(i)));
        }
    }

    @Test
    void aresalaNoTieneUrlYSoloLaBodegaEsCircular() throws Exception {

        mockMvc.perform(get("/api/public/patrocinadores")).andExpect(jsonPath("$[4].nombre").value("Aresala"))
                .andExpect(jsonPath("$[4].url").doesNotExist()).andExpect(jsonPath("$[3].circular").value(true))
                .andExpect(jsonPath("$[0].circular").value(false)).andExpect(jsonPath("$[1].circular").value(false))
                .andExpect(jsonPath("$[2].circular").value(false)).andExpect(jsonPath("$[4].circular").value(false))
                .andExpect(jsonPath("$[5].circular").value(false)).andExpect(jsonPath("$[6].circular").value(false));
    }

    @Test
    void elPieDeLaWebPintaLosSieteConSusEnlacesYSinEnlaceEnAresala() throws Exception {

        String html = mockMvc.perform(get("/politicaPrivacidad")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        for (Patrocinador p : PatrocinadoresClub.LISTA) {
            assertThat(html).contains("alt=\"" + p.nombre() + "\"").contains("src=\"/images/" + p.imagen() + "\"");

            if (p.url() != null) {
                assertThat(html).contains("<a href=\"" + p.url() + "\" target=\"_blank\" rel=\"noopener\">");
            } else {
                assertThat(html).doesNotContain("href=\"" + p.url() + "\"");
            }
        }

        // Aresala: la imagen no va dentro de un <a>
        int img = html.indexOf("src=\"/images/aresala.jpg\"");
        int inicioImg = html.lastIndexOf("<img", img);
        assertThat(html.substring(Math.max(0, inicioImg - 40), inicioImg)).doesNotContain("<a ");
        assertThat(html.indexOf("<a ", html.lastIndexOf("</a>", img))).isGreaterThan(img);

        // Estilo redondo solo en La bodega
        assertThat(html.split("border-radius:50%", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("class=\"patrocinador-img img-fluid\"");
    }

    @Test
    void cadaImagenDeLaListaExisteEnStaticImages() {

        for (Patrocinador p : PatrocinadoresClub.LISTA) {
            assertThat(new ClassPathResource("static/images/" + p.imagen()).exists()).as(p.imagen()).isTrue();
        }
    }
}
