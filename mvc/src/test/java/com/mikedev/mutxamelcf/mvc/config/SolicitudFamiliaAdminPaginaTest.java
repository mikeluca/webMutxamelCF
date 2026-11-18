package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Coincidencia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.HijoRevision;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;

/** La pantalla "Solicitudes de familia" del panel renderizada de verdad para SUPER y ADMIN, con el menú. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolicitudFamiliaAdminPaginaTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private SolicitudFamiliaService solicitudFamiliaService;

    @Autowired
    private MockMvc mockMvc;

    private static Revision pendiente() {

        HijoRevision h = new HijoRevision(31L, "Luis", "Pérez", "Alevín B", "PENDIENTE", null,
                List.of(new Coincidencia(55L, "Luis Pérez Gómez", 8L, "Alevín B", true)));

        return new Revision(12L, "Ana", "Pérez Gil", "ana@example.com", "623176818", "MADRE", "PENDIENTE", null,
                LocalDateTime.of(2026, 11, 18, 10, 0), null, List.of(h));
    }

    private String pagina(String rol) throws Exception {
        return mockMvc.perform(get("/admin/solicitudes-familia").with(user("panel").roles(rol)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void superYAdminVenLasPendientesConSusCoincidenciasYLosBotones() throws Exception {

        when(solicitudFamiliaService.pendientes(any())).thenReturn(List.of(pendiente()));
        when(solicitudFamiliaService.historico(any())).thenReturn(List.of());

        for (String rol : List.of("SUPER", "ADMIN")) {

            String html = pagina(rol);

            assertThat(html).contains("Solicitudes de familia").contains("Ana Pérez Gil").contains("ana@example.com")
                    .contains("Luis Pérez").contains("Alevín B")
                    .contains("Luis Pérez Gómez — Alevín B (mismo equipo)")
                    .contains("/admin/solicitudes-familia/hijos/31/aprobar")
                    .contains("/admin/solicitudes-familia/hijos/31/rechazar").contains("Aprobar").contains("Rechazar")
                    .contains("Últimos 7 días");
        }
    }

    @Test
    void sinPendientesNiHistoricoLoDice() throws Exception {

        when(solicitudFamiliaService.pendientes(any())).thenReturn(List.of());
        when(solicitudFamiliaService.historico(any())).thenReturn(List.of());

        String html = pagina("SUPER");

        assertThat(html).contains("No hay solicitudes pendientes.")
                .contains("Ninguna solicitud revisada en los últimos 7 días.");
    }

    @Test
    void elHistoricoMuestraLasRevisadasConSuEstadoYMotivo() throws Exception {

        HijoRevision aprobado = new HijoRevision(31L, "Luis", "Pérez", null, "APROBADO",
                new Coincidencia(55L, "Luis Pérez Gómez", 8L, "Alevín B", false), List.of());
        Revision r = new Revision(12L, "Ana", "Pérez Gil", "ana@example.com", null, "MADRE", "APROBADA", null,
                LocalDateTime.of(2026, 11, 18, 10, 0), LocalDateTime.of(2026, 11, 19, 9, 30), List.of(aprobado));

        when(solicitudFamiliaService.pendientes(any())).thenReturn(List.of());
        when(solicitudFamiliaService.historico(any())).thenReturn(List.of(r));

        String html = pagina("ADMIN");

        assertThat(html).contains("Aprobada").contains("Aprobado: Luis Pérez Gómez").contains("19/11/2026 09:30");
    }

    @Test
    void elMenuDeAdministracionTieneElEnlaceParaSuperYAdmin() throws Exception {

        when(solicitudFamiliaService.pendientes(any())).thenReturn(List.of());
        when(solicitudFamiliaService.historico(any())).thenReturn(List.of());

        assertThat(pagina("SUPER")).contains("href=\"/admin/solicitudes-familia\"");
        assertThat(pagina("ADMIN")).contains("href=\"/admin/solicitudes-familia\"");
    }
}
