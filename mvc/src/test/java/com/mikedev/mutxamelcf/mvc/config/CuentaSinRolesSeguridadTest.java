package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;

/**
 * Una cuenta de la app SIN roles (la de una familia que ha pedido acceso y el club aún no ha aprobado) con la
 * configuración real de seguridad: solo llega a lo mínimo para ver el estado de su solicitud y a nada de jugadores,
 * equipos, calendario, convocatorias ni comunicaciones. Una cuenta con algún rol no cambia.
 *
 * <p>
 * Los endpoints se piden SIN datos reales: el filtro de seguridad responde 403 antes de tocar ningún servicio, así que
 * lo que se prueba es que ni se llega al controlador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CuentaSinRolesSeguridadTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private SolicitudFamiliaService solicitudFamiliaService;

    @Autowired
    private MockMvc mockMvc;

    /** Lo privado de la app: jugadores, calendario, convocatorias, comunicaciones y el resto. */
    static Stream<Arguments> rutasPrivadas() {
        return Stream.of(
                // Jugadores y equipos
                Arguments.of("GET", "/api/app/equipos/jugadores"),
                Arguments.of("GET", "/api/app/equipos/1/jugadores"),
                Arguments.of("GET", "/api/app/jugadores/1"),
                Arguments.of("PUT", "/api/app/jugadores/1/dorsal-posicion"),
                Arguments.of("GET", "/api/app/temporadas"),
                // Calendario y entrenamientos
                Arguments.of("GET", "/api/app/calendario?equipoId=1&desde=2026-11-01&hasta=2026-11-30"),
                Arguments.of("GET", "/api/app/entrenamientos"),
                Arguments.of("GET", "/api/app/sesiones-entrenamiento"),
                Arguments.of("GET", "/api/app/horarios-entrenamiento"),
                // Convocatorias y partidos
                Arguments.of("GET", "/api/app/convocatorias"),
                Arguments.of("GET", "/api/app/convocatorias/1"),
                Arguments.of("GET", "/api/app/partidos/1/convocatoria"),
                Arguments.of("GET", "/api/app/partidos/sin-convocatoria?equipoId=1"),
                Arguments.of("GET", "/api/app/partidos/1/estadisticas"),
                Arguments.of("POST", "/api/app/partidos/1/justificaciones"),
                // Comunicaciones
                Arguments.of("GET", "/api/app/comunicaciones"),
                Arguments.of("GET", "/api/app/comunicaciones/1"),
                Arguments.of("POST", "/api/app/comunicaciones"),
                // Otros
                Arguments.of("GET", "/api/app/continuidad"),
                Arguments.of("GET", "/api/app/cuotas"),
                Arguments.of("GET", "/api/app/coordinacion/fin-de-semana"),
                Arguments.of("GET", "/api/app/imagenes-redes"),
                Arguments.of("GET", "/api/app/admin/cuentas"),
                Arguments.of("GET", "/api/app/reparto"),
                Arguments.of("GET", "/api/app/moderacion"));
    }

    private static MockHttpServletRequestBuilder peticion(String metodo, String ruta) {
        MockHttpServletRequestBuilder b = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "DELETE" -> delete(ruta);
            default -> get(ruta);
        };

        return b.contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasPrivadas")
    void unaCuentaSinRolesRecibe403EnTodoLoPrivado(String metodo, String ruta) throws Exception {

        mockMvc.perform(peticion(metodo, ruta).with(user("100").roles())).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasPrivadas")
    void sinSesionSigueSiendo401(String metodo, String ruta) throws Exception {

        mockMvc.perform(peticion(metodo, ruta)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasPrivadas")
    void unaCuentaConAlgunRolPasaElFiltroDeSeguridad(String metodo, String ruta) throws Exception {

        // Con un rol la petición llega al controlador (que, sin base de datos de test, puede fallar por dentro o
        // responder lo que sea). Lo que no puede es quedarse en el 403 / 401 del filtro de seguridad.
        int estado;

        try {
            estado = mockMvc.perform(peticion(metodo, ruta).with(user("100").roles("FAMILIAR"))).andReturn()
                    .getResponse().getStatus();
        } catch (Exception e) {
            estado = 200;
        }

        assertThat(estado).as("%s %s", metodo, ruta).isNotIn(401, 403);
    }

    @Test
    void laCuentaSinRolesSiPuedeVerElEstadoDeSuSolicitudYPedirElAcceso() throws Exception {

        when(solicitudFamiliaService.misSolicitudes(100)).thenReturn(List.of());

        mockMvc.perform(get("/api/app/solicitudes-familia/mias").with(user("100").roles()))
                .andExpect(status().isOk());

        // Pedir el acceso con sesión pasa el filtro (el servicio simulado devuelve null: 201 sin cuerpo).
        mockMvc.perform(post("/api/app/solicitudes-familia").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentesco\":\"MADRE\"}").with(user("100").roles()).with(csrf()))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    void laCuentaSinRolesPuedeLoMinimoParaRecibirLaRespuestaYBorrarSuCuenta() throws Exception {

        for (String ruta : List.of("/api/app/notificaciones", "/api/app/perfil", "/api/app/auth/me",
                "/api/app/preferencias-notificacion", "/api/app/preferencias/idioma")) {

            int estado;

            try {
                estado = mockMvc.perform(get(ruta).with(user("100").roles())).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                estado = 200;
            }

            assertThat(estado).as(ruta).isNotEqualTo(403);
        }
    }

    @Test
    void siemprePuedeIniciarSesionYRegistrarseSinSesion() throws Exception {

        // El registro es público: no pasa por el filtro de la app. (El servicio simulado devuelve null.)
        mockMvc.perform(post("/api/public/solicitudes-familia/reenviar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@example.com\"}")).andExpect(status().isNoContent());
    }

    @Test
    void lasRutasDeRevisionLasRechazaElServicioSinPermisoNoElFiltro() throws Exception {

        when(solicitudFamiliaService.pendientes(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new SecurityException("No puedes revisar solicitudes de familia"));

        // Pasa el filtro (está en /solicitudes-familia/**) y el controlador la convierte en 403.
        mockMvc.perform(get("/api/app/solicitudes-familia/pendientes").with(user("100").roles()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elPanelDeSolicitudesEsSoloParaSuperYAdmin() throws Exception {

        mockMvc.perform(get("/admin/solicitudes-familia").with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/solicitudes-familia").with(user("sinrol").roles("OTRO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/solicitudes-familia/hijos/1/aprobar").param("jugadorId", "5")
                .with(user("entrenador").roles("ENTRENADOR")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/solicitudes-familia")).andExpect(status().is3xxRedirection());
    }
}
