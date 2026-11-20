package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;
import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.model.PersonasVinculablesResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioAppAdminResponse;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * El menú "Administración de la App" de la cabecera del panel y los filtros de "Usuarios de la App", con las
 * páginas renderizadas de verdad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracionAppPaginaTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @MockBean
    private UsuarioAppService usuarioAppService;

    @MockBean
    private RolAppDao rolAppDao;

    @MockBean
    private SolicitudFamiliaService solicitudFamiliaService;

    @Autowired
    private MockMvc mockMvc;

    private static RolApp rol(int id, String codigo, String nombre) {
        RolApp rol = new RolApp();
        rol.setId(id);
        rol.setCodigo(codigo);
        rol.setNombre(nombre);
        return rol;
    }

    private static UsuarioAppAdminResponse cuenta(int id, String email, String nombre, boolean activo, boolean pendiente,
            boolean expirado, String... roles) {
        UsuarioAppAdminResponse u = new UsuarioAppAdminResponse();
        u.setId(id);
        u.setEmail(email);
        u.setNombre(nombre);
        u.setActivo(activo);
        u.setTokenPendiente(pendiente);
        u.setTokenExpirado(expirado);
        u.setRoles(List.of(roles));
        u.setVinculos(List.of());
        u.setFechaAlta(Timestamp.valueOf("2026-11-01 10:00:00"));
        return u;
    }

    private String pagina(String url, String rolPanel) throws Exception {
        when(usuarioAppService.obtenerPersonasVinculables()).thenReturn(new PersonasVinculablesResponse());

        return mockMvc.perform(get(url).with(user("panel").roles(rolPanel))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String menuApp(String html) {
        int inicio = html.indexOf("navbarDropdownApp");
        assertThat(inicio).as("el desplegable Administración de la App").isPositive();
        int fin = html.indexOf("</ul>", inicio);
        return html.substring(inicio, fin);
    }

    @Test
    void superVeElMenuConLasCincoOpcionesEnOrdenYAdminConCuatroSinUsuariosApp() throws Exception {

        when(usuarioAppService.listarUsuariosAdmin()).thenReturn(List.of());
        when(rolAppDao.obtenerTodos()).thenReturn(List.of());

        String superMenu = menuApp(pagina("/admin/usuarios-app", "SUPER"));

        assertThat(superMenu).contains("Administración de la App");
        assertThat(List.of(superMenu.indexOf("/admin/usuarios-app"), superMenu.indexOf("/admin/solicitudes-familia"),
                superMenu.indexOf("/admin/escudos-rivales"), superMenu.indexOf("/admin/fondos-carteles"),
                superMenu.indexOf("/admin/equipajes"))).doesNotContain(-1).isSorted();

        when(solicitudFamiliaService.pendientes(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(solicitudFamiliaService.historico(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        String adminMenu = menuApp(pagina("/admin/solicitudes-familia", "ADMIN"));

        assertThat(adminMenu).doesNotContain("/admin/usuarios-app").contains("/admin/solicitudes-familia")
                .contains("/admin/escudos-rivales").contains("/admin/fondos-carteles").contains("/admin/equipajes");
    }

    @Test
    void lasCincoOpcionesYaNoEstanEnAdministracionDeportivaNiHayEnlaceSueltoDeUsuariosApp() throws Exception {

        when(solicitudFamiliaService.pendientes(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(solicitudFamiliaService.historico(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        String html = pagina("/admin/solicitudes-familia", "SUPER");
        String deportiva = html.substring(html.indexOf("navbarDropdownDeportivo"), html.indexOf("navbarDropdownPagos"));

        assertThat(deportiva).doesNotContain("/admin/equipajes").doesNotContain("/admin/fondos-carteles")
                .doesNotContain("/admin/escudos-rivales").doesNotContain("/admin/solicitudes-familia");
        assertThat(html.split("href=\"/admin/usuarios-app\"", -1).length - 1).isEqualTo(1);
        assertThat(html.split("href=\"/admin/equipajes\"", -1).length - 1).isEqualTo(1);
    }

    @Test
    void usuariosAppTraeLosFiltrosYCadaFilaSusDatosDeFiltrado() throws Exception {

        when(rolAppDao.obtenerTodos()).thenReturn(List.of(rol(1, "JUGADOR", "Jugador"),
                rol(2, "RETRANSMISION", "Retransmisión"), rol(3, "RRSS", "Redes sociales")));
        when(usuarioAppService.listarUsuariosAdmin()).thenReturn(List.of(
                cuenta(1, "ana@example.com", "Ana Pérez", true, false, false, "RRSS", "RETRANSMISION"),
                cuenta(2, "luis@example.com", "Luis", false, true, false, "JUGADOR"),
                cuenta(3, "marta@example.com", "Marta", false, true, true, "JUGADOR"),
                cuenta(4, "pepe@example.com", "Pepe", false, false, false, "RRSS"),
                cuenta(5, "sinroles@example.com", "Sin Roles", true, false, false)));

        String html = pagina("/admin/usuarios-app", "SUPER");

        // El desplegable de roles sale de ROLES_APP, en orden, con "Todos" y "Sin roles".
        assertThat(html).contains("Todos los roles").contains("Sin roles").contains("Todos los estados")
                .contains("Buscar por nombre o email…").contains("Limpiar filtros");
        assertThat(html.indexOf("value=\"JUGADOR\"")).isLessThan(html.indexOf("value=\"RETRANSMISION\""));
        assertThat(html.indexOf("value=\"RETRANSMISION\"")).isLessThan(html.indexOf("value=\"RRSS\""));
        assertThat(html).contains(">Redes sociales</option>");

        assertThat(fila(html, 1)).contains("data-estado=\"ACTIVA\"").contains("data-roles=\" RRSS RETRANSMISION \"")
                .contains("data-nombre-email=\"ana perez ana@example.com\"");
        assertThat(fila(html, 2)).contains("data-estado=\"PENDIENTE\"").contains("data-roles=\" JUGADOR \"");
        assertThat(fila(html, 3)).contains("data-estado=\"CADUCADA\"");
        assertThat(fila(html, 4)).contains("data-estado=\"DESACTIVADA\"").contains("data-roles=\" RRSS \"");
        assertThat(fila(html, 5)).contains("data-estado=\"ACTIVA\"").contains("data-roles=\"  \"");

        // La columna Rol enseña los nombres, no los códigos.
        assertThat(fila(html, 1)).contains("Redes sociales").contains("Retransmisión")
                .doesNotContain(">RETRANSMISION<");
        assertThat(html).contains("Ninguna cuenta cumple los filtros");
    }

    private static String fila(String html, int id) {
        Matcher m = Pattern.compile("<tr id=\"fila-usuario-" + id + "\".*?</tr>", Pattern.DOTALL).matcher(html);
        assertThat(m.find()).as("fila " + id).isTrue();
        return m.group();
    }
}
