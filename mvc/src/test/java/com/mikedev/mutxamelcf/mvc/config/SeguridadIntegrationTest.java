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

    @Test
    void filtroJwtNoAfectaARutasFueraDeApiApp() throws Exception {

        // Un header Authorization invalido no debe romper rutas fuera de /api/app/**:
        // el filtro JWT tiene que ignorarlas por completo (RequestMatcher).
        mockMvc.perform(get("/")
                        .header("Authorization", "Bearer token-completamente-invalido"))
                .andExpect(status().isOk());
    }
}
