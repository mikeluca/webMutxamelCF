package com.mikedev.mutxamelcf.mvc.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;
import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse.TextoPublicacion;
import com.mikedev.mutxamelcf.service.CartelNoDisponibleException;
import com.mikedev.mutxamelcf.service.CartelPrimerEquipoService;
import com.mikedev.mutxamelcf.service.ImagenesRedesService;

/**
 * Las rutas de imágenes para redes: solo RETRANSMISION y ADMIN_APP (lo comprueban los servicios y el controlador lo
 * traduce a 403), el contrato del cartel del próximo partido y sus códigos de error.
 */
class AppImagenesRedesControllerTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

    private final ImagenesRedesService imagenes = mock(ImagenesRedesService.class);
    private final CartelPrimerEquipoService cartel = mock(CartelPrimerEquipoService.class);

    private MockMvc mockMvc;
    private Authentication usuario;

    @BeforeEach
    void setUp() {
        // Como en la aplicación: JSON con fechas ISO (spring.jackson.serialization.write-dates-as-timestamps=false).
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new AppImagenesRedesController(imagenes, cartel))
                .setMessageConverters(new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new org.springframework.http.converter.StringHttpMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper))
                .build();

        usuario = new TestingAuthenticationToken("7", null);
        usuario.setAuthenticated(true);
    }

    // ---- permisos -----------------------------------------------------------------------------------------

    @Test
    void sinSesionTodasLasRutasDan401() throws Exception {

        mockMvc.perform(get("/api/app/imagenes-redes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/app/imagenes-redes/3/png")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/app/imagenes-redes/regenerar").param("tipo", "PREVIA"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinRolDeRetransmisionNiAdminTodasLasRutasDan403IncluidoElCoordinador() throws Exception {

        // Los servicios lanzan SecurityException a quien no tiene RETRANSMISION ni ADMIN_APP (p. ej. COORDINADOR).
        when(imagenes.listar(anyLong(), any())).thenThrow(new SecurityException("sin acceso"));
        when(imagenes.descargar(anyLong(), anyLong())).thenThrow(new SecurityException("sin acceso"));
        when(imagenes.regenerar(anyLong(), anyString(), any())).thenThrow(new SecurityException("sin acceso"));
        when(cartel.partido(anyLong())).thenThrow(new SecurityException("sin acceso"));
        when(cartel.png(anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new SecurityException("sin acceso"));

        mockMvc.perform(get("/api/app/imagenes-redes").principal(usuario)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/app/imagenes-redes/3/png").principal(usuario)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/app/imagenes-redes/regenerar").param("tipo", "PREVIA").principal(usuario))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo").principal(usuario).accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario))
                .andExpect(status().isForbidden());
    }

    // ---- GET partido-primer-equipo ---------------------------------------------------------------------------

    @Test
    void elPartidoDevuelveElContratoJson() throws Exception {

        when(cartel.partido(7L)).thenReturn(new PartidoPrimerEquipoResponse(true, null, 30L, true,
                "C.D. Salesianos Alicante", "/api/public/escudos-rivales/40?v=2", LocalDate.of(2026, 11, 21), "17:30",
                "Camp Nou Los Olmos (Mutxamel)", "Primera FFCV, Grup 4", 11, "LIGA",
                new TextoPublicacion("texto es", "text ca")));

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo").principal(usuario).accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.partidoId").value(30)).andExpect(jsonPath("$.local").value(true))
                .andExpect(jsonPath("$.rival").value("C.D. Salesianos Alicante"))
                .andExpect(jsonPath("$.escudoRivalUrl").value("/api/public/escudos-rivales/40?v=2"))
                .andExpect(jsonPath("$.dia").value("2026-11-21")).andExpect(jsonPath("$.hora").value("17:30"))
                .andExpect(jsonPath("$.campo").value("Camp Nou Los Olmos (Mutxamel)"))
                .andExpect(jsonPath("$.competicion").value("Primera FFCV, Grup 4"))
                .andExpect(jsonPath("$.jornada").value(11)).andExpect(jsonPath("$.tipo").value("LIGA"))
                .andExpect(jsonPath("$.textoPublicacion.es").value("texto es"))
                .andExpect(jsonPath("$.textoPublicacion.ca").value("text ca"));
    }

    @Test
    void siNoEstaDisponibleSoloVienenDisponibleYMotivo() throws Exception {

        when(cartel.partido(7L)).thenReturn(PartidoPrimerEquipoResponse.noDisponible("YA_EMPEZADO"));

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo").principal(usuario).accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.motivo").value("YA_EMPEZADO")).andExpect(jsonPath("$.partidoId").doesNotExist());
    }

    // ---- GET partido-primer-equipo/png ---------------------------------------------------------------------------

    @Test
    void elPngSeDevuelveSinCache() throws Exception {

        when(cartel.png(7L, "HISTORIA", "ca", true, false, true)).thenReturn(PNG);

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario)
                .param("formato", "HISTORIA").param("idioma", "ca").param("racha", "true").param("qr", "false")
                .param("hashtags", "true")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(PNG));
    }

    @Test
    void porDefectoPublicacionEspanolYLosTresOpcionalesActivos() throws Exception {

        when(cartel.png(7L, "PUBLICACION", "es", true, true, true)).thenReturn(PNG);

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));

        verify(cartel).png(eq(7L), eq("PUBLICACION"), eq("es"), eq(true), eq(true), eq(true));
    }

    @Test
    void siNoEstaDisponibleDa409ConElCodigo() throws Exception {

        when(cartel.png(anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new CartelNoDisponibleException("SIN_PARTIDO"));

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SIN_PARTIDO"));

        org.mockito.Mockito.doThrow(new CartelNoDisponibleException("YA_EMPEZADO")).when(cartel).png(anyLong(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("YA_EMPEZADO"));
    }

    @Test
    void unParametroNoValidoDa400() throws Exception {

        when(cartel.png(anyLong(), eq("CUADRADO"), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalArgumentException("El formato debe ser PUBLICACION o HISTORIA"));
        when(cartel.png(anyLong(), anyString(), eq("en"), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalArgumentException("El idioma debe ser es o ca"));

        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario)
                .param("formato", "CUADRADO")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario)
                .param("idioma", "en")).andExpect(status().isBadRequest());
        // Un booleano que no lo es (racha=quizas) lo rechaza Spring antes del servicio.
        mockMvc.perform(get("/api/app/imagenes-redes/partido-primer-equipo/png").principal(usuario)
                .param("racha", "quizas")).andExpect(status().isBadRequest());
    }

    // ---- las rutas de siempre -----------------------------------------------------------------------------

    @Test
    void lasRutasDePreviaYResultadosSiguenIgual() throws Exception {

        when(imagenes.descargar(7L, 3L)).thenReturn(PNG);

        mockMvc.perform(get("/api/app/imagenes-redes/3/png").principal(usuario)).andExpect(status().isOk())
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/app/imagenes-redes").param("fecha", "2026-11-14").principal(usuario))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/app/imagenes-redes").param("fecha", "ayer").principal(usuario))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/app/imagenes-redes/regenerar").param("tipo", "PREVIA").principal(usuario))
                .andExpect(status().isOk());

        verify(imagenes).regenerar(7L, "PREVIA", null);
    }
}
