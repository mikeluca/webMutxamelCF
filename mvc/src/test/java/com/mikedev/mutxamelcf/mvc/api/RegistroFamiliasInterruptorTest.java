package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.mvc.config.PublicFormRateLimiter;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Registro;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Resultado;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/** El interruptor app.registro-familias.habilitado: GET /funciones y el bloqueo de POST /solicitudes-familia. */
class RegistroFamiliasInterruptorTest {

    private static final MappingJackson2HttpMessageConverter JSON = new MappingJackson2HttpMessageConverter(
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

    private static final String PETICION = """
            {"nombre":"Ana","apellidos":"Pérez","email":"ana@example.com","parentesco":"MADRE",
             "hijos":[{"nombre":"Luis","apellidos":"Pérez"}],"aceptaPrivacidad":true,"declaraRepresentante":true}
            """;

    private final SolicitudFamiliaService solicitudes = mock(SolicitudFamiliaService.class);
    private final UsuarioAppService usuarios = mock(UsuarioAppService.class);
    private final ComunicacionesService comunicaciones = mock(ComunicacionesService.class);

    private MockMvc mvc(boolean habilitado) {
        return MockMvcBuilders.standaloneSetup(
                new PublicAppFuncionesController(habilitado),
                new PublicSolicitudFamiliaController(solicitudes, usuarios, comunicaciones,
                        new PublicFormRateLimiter(), habilitado))
                .setMessageConverters(JSON).build();
    }

    // ---- GET /api/public/app/funciones ---------------------------------------------------------------------------------

    @Test
    void funcionesDiceQueElRegistroEstaDeshabilitado() throws Exception {
        mvc(false).perform(get("/api/public/app/funciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registroFamiliasHabilitado").value(false));
    }

    @Test
    void funcionesDiceQueElRegistroEstaHabilitado() throws Exception {
        mvc(true).perform(get("/api/public/app/funciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registroFamiliasHabilitado").value(true));
    }

    /** Sin tocar nada, el application.properties real deja el registro cerrado. */
    @Test
    void porDefectoElFicheroRealTieneElInterruptorADesactivado() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        // Si APP_REGISTRO_FAMILIAS estuviera definida en la máquina, ganaría sobre el valor por defecto del fichero.
        assertThat(System.getenv("APP_REGISTRO_FAMILIAS")).isNull();

        assertThat(env.getRequiredProperty("app.registro-familias.habilitado", Boolean.class)).isFalse();
    }

    // ---- POST /api/public/solicitudes-familia --------------------------------------------------------------------------

    @Test
    void conElInterruptorADesactivadoElPostDevuelve403SinCrearNada() throws Exception {
        mvc(false).perform(post("/api/public/solicitudes-familia").contentType(MediaType.APPLICATION_JSON)
                .content(PETICION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value("REGISTRO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("El registro de familias estará disponible próximamente. "
                        + "Mientras tanto, pide la invitación a tu entrenador o al club."));

        // Ni cuenta ni solicitud ni email.
        verifyNoInteractions(solicitudes, comunicaciones, usuarios);
    }

    @Test
    void conElInterruptorADesactivadoTambienBloqueaUnCuerpoVacio() throws Exception {
        mvc(false).perform(post("/api/public/solicitudes-familia"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value("REGISTRO_NO_DISPONIBLE"));

        verifyNoInteractions(solicitudes, comunicaciones, usuarios);
    }

    @Test
    void conElInterruptorActivadoElPostCreaLaSolicitudYMandaElCodigo() throws Exception {
        when(solicitudes.registrarPublica(any())).thenReturn(
                new Registro(Resultado.CODIGO_ENVIADO, "ana@example.com", "Ana", "123456", "es"));
        when(comunicaciones.enviarCodigoSolicitudFamilia(any(), any(), any(), any())).thenReturn(true);

        mvc(true).perform(post("/api/public/solicitudes-familia").contentType(MediaType.APPLICATION_JSON)
                .content(PETICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CODIGO_ENVIADO"));

        verify(comunicaciones).enviarCodigoSolicitudFamilia("ana@example.com", "Ana", "123456", "es");
    }

    @Test
    void verificarYReenviarSiguenFuncionandoConElInterruptorADesactivado() throws Exception {
        UsuarioApp cuenta = new UsuarioApp();
        cuenta.setId(100);
        cuenta.setEmail("ana@example.com");
        LoginAppResponse login = new LoginAppResponse();
        login.setUsuarioId(100);
        when(solicitudes.verificarEmail("ana@example.com", "123456", "Secreta123")).thenReturn(cuenta);
        when(usuarios.login("ana@example.com", "Secreta123")).thenReturn(login);

        MockMvc mvc = mvc(false);

        mvc.perform(post("/api/public/solicitudes-familia/verificar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@example.com\",\"codigo\":\"123456\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/public/solicitudes-familia/reenviar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@example.com\"}"))
                .andExpect(status().isNoContent());
    }
}
