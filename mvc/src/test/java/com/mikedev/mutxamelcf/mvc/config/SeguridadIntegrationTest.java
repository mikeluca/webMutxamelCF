package com.mikedev.mutxamelcf.mvc.config;

import com.google.firebase.FirebaseApp;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests negativos de seguridad sobre la configuracion real
 * (SecurityConfig + CustomAuthenticationProvider + JwtAuthenticationFilter
 * + LoginRateLimiter), sin mockear las reglas de autorizacion.
 *
 * FirebaseApp se mockea porque su bean real abre un fichero de
 * credenciales que no existe en el entorno de test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadIntegrationTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void loginWebConCredencialesInvalidasRedirigeConError() throws Exception {

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "usuario_test_credenciales_invalidas")
                        .param("password", "loQueSea"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login?error=true")));
    }

    @Test
    void paginaDeLoginIncluyeElTokenCsrf() throws Exception {

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }

    @Test
    void loginWebSinTokenCsrfEsRechazado() throws Exception {

        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "loQueSea"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWebTrasVariosFallosQuedaBloqueado() throws Exception {

        String usuario = "usuario_test_bloqueo";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", usuario)
                            .param("password", "incorrecta"))
                    .andExpect(status().is3xxRedirection());
        }

        // El sexto intento ya deberia estar bloqueado, aunque la contrasena fuese correcta
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", usuario)
                        .param("password", "incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login?error=bloqueado")));
    }

    @Test
    void accederAAdminSinSesionRedirigeALogin() throws Exception {

        mockMvc.perform(get("/admin/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
    }

    @Test
    void apiAppSinTokenDevuelve401() throws Exception {

        mockMvc.perform(get("/api/app/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiAppConTokenManipuladoDevuelve401() throws Exception {

        // Firmado con una clave distinta a la real: la firma no puede ser valida
        SecretKey otraClave = Keys.hmacShaKeyFor(
                "OtraClaveQueNoEsLaDeLaAplicacionDePrueba1234".getBytes(StandardCharsets.UTF_8));

        String tokenManipulado = Jwts.builder()
                .subject("1")
                .claim("email", "quien-sea@mutxamelcf.es")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otraClave)
                .compact();

        mockMvc.perform(get("/api/app/auth/me")
                        .header("Authorization", "Bearer " + tokenManipulado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiAppConTokenCaducadoDevuelve401() throws Exception {

        SecretKey claveReal = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String tokenCaducado = Jwts.builder()
                .subject("1")
                .claim("email", "quien-sea@mutxamelcf.es")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(claveReal)
                .compact();

        mockMvc.perform(get("/api/app/auth/me")
                        .header("Authorization", "Bearer " + tokenCaducado))
                .andExpect(status().isUnauthorized());
    }

    /*
     * N-01: sin token valido, este DELETE debe responder 204 en vez de
     * 401, para que un cliente con el token ya caducado/invalido pueda
     * terminar su logout sin que ApiClient reciba un 401 que dispare de
     * nuevo cerrarSesion() (bucle infinito, ver 2a auditoria).
     */
    @Test
    void deleteDispositivosSinTokenValidoDevuelve204() throws Exception {

        mockMvc.perform(delete("/api/app/dispositivos")
                        .param("tokenFcm", "cualquier-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void filtroJwtNoAfectaARutasFueraDeApiApp() throws Exception {

        // Un header Authorization invalido no debe romper rutas fuera de /api/app/**:
        // el filtro JWT tiene que ignorarlas por completo (RequestMatcher).
        mockMvc.perform(get("/")
                        .header("Authorization", "Bearer token-completamente-invalido"))
                .andExpect(status().isOk());
    }

    /*
     * SEC-03: un usuario web ENTRENADOR (no SUPER) no puede acceder a
     * nada bajo /admin/pagos/**, ni siquiera de lectura. Antes de la
     * correccion, CuotaJugadorController/ConceptoPagoController/
     * TemporadaController colgaban de /admin/** sin exigir SUPER, asi
     * que un ENTRENADOR podia borrar cuotas y pagos de otros equipos;
     * esos tres controladores se han eliminado (su funcionalidad ya
     * vive en PagosController, protegido por ROLE_SUPER) y /admin/**
     * ya no acepta un authenticated() generico.
     */
    @Test
    void entrenadorWebRecibe403EnPagos() throws Exception {

        mockMvc.perform(get("/admin/pagos")
                        .with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void entrenadorWebRecibe403AlIntentarBorrarUnaCuota() throws Exception {

        mockMvc.perform(post("/admin/pagos/cuotas/borrar")
                        .param("id", "1")
                        .with(user("entrenador").roles("ENTRENADOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    /*
     * ADMIN es un rol web equivalente a ENTRENADOR: entra al panel
     * general (ver adminWebPuedeAccederAAdminGeneral), pero sigue sin
     * poder acceder a /admin/pagos ni /admin/usuarios-app, que exigen
     * SUPER explicitamente.
     */
    @Test
    void adminWebRecibe403EnPagos() throws Exception {

        mockMvc.perform(get("/admin/pagos")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWebRecibe403AlIntentarBorrarUnaCuota() throws Exception {

        mockMvc.perform(post("/admin/pagos/cuotas/borrar")
                        .param("id", "1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWebPuedeAccederAAdminGeneral() throws Exception {

        // Se pide una ruta bajo /admin/** que no existe para comprobar
        // que el filtro de seguridad NO devuelve 403 (a diferencia de un
        // rol no reconocido, ver usuarioSinRolAdmiteRedirigeAAdmin) sin
        // depender de las tablas que el esquema de test no tiene: si el
        // rol ADMIN pasa el filtro, el 404 lo da el dispatcher de Spring
        // MVC al no encontrar un handler, no la seguridad.
        mockMvc.perform(get("/admin/ruta-que-no-existe")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    /*
     * No se comprueba aqui el camino positivo ("SUPER puede entrar a
     * /admin/pagos") porque el esquema H2 de este perfil de test solo
     * crea la tabla USUARIOS (ver TST-01 de la auditoria): cualquier
     * peticion de un usuario autorizado que llegue a tocar
     * PagosController fallaria por falta de tablas, no por
     * autorizacion. Los tests negativos de abajo ya demuestran que la
     * regla hasAnyRole("SUPER","ENTRENADOR") + hasRole("SUPER") se
     * aplica antes de llegar al controlador.
     */

    @Test
    void usuarioSinRolAdmiteRedirigeAAdmin() throws Exception {

        // Un usuario autenticado pero sin ROLE_SUPER ni ROLE_ENTRENADOR
        // (por ejemplo, un rol futuro no contemplado) no debe poder
        // entrar en ninguna pantalla de /admin/**.
        mockMvc.perform(get("/admin/admin")
                        .with(user("desconocido").roles("SIN_ROL_RECONOCIDO")))
                .andExpect(status().isForbidden());
    }
}
