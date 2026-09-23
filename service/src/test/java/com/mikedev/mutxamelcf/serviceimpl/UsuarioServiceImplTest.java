package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.UsuarioDao;
import com.mikedev.mutxamelcf.model.Usuario;
import com.mikedev.mutxamelcf.model.UsuarioDTO;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioDao usuarioDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioServiceImpl(usuarioDao, passwordEncoder);
    }

    @Test
    void validarUsuarioDevuelveDtoCuandoElHashCoincide() {
        Usuario usuarioBD = new Usuario();
        usuarioBD.setUsuario("admin");
        usuarioBD.setPassword("$2a$10$hashValido");
        usuarioBD.setRol("SUPER");

        when(usuarioDao.obtenerPorUsuario("admin")).thenReturn(usuarioBD);
        when(passwordEncoder.matches(eq("laContrasenaCorrecta"), eq("$2a$10$hashValido"))).thenReturn(true);

        UsuarioDTO resultado = usuarioService.validarUsuario("admin", "laContrasenaCorrecta");

        assertThat(resultado).isNotNull();
        assertThat(resultado.getUsuario()).isEqualTo("admin");
        assertThat(resultado.getRol()).isEqualTo("SUPER");
    }

    @Test
    void validarUsuarioDevuelveNullCuandoElHashNoCoincide() {
        Usuario usuarioBD = new Usuario();
        usuarioBD.setUsuario("admin");
        usuarioBD.setPassword("$2a$10$hashValido");
        usuarioBD.setRol("SUPER");

        when(usuarioDao.obtenerPorUsuario("admin")).thenReturn(usuarioBD);
        when(passwordEncoder.matches(eq("contrasenaIncorrecta"), eq("$2a$10$hashValido"))).thenReturn(false);

        UsuarioDTO resultado = usuarioService.validarUsuario("admin", "contrasenaIncorrecta");

        assertThat(resultado).isNull();
    }

    @Test
    void validarUsuarioDevuelveNullCuandoElUsuarioNoExiste() {
        when(usuarioDao.obtenerPorUsuario("no_existe")).thenReturn(null);

        UsuarioDTO resultado = usuarioService.validarUsuario("no_existe", "loQueSea");

        assertThat(resultado).isNull();
    }

    @Test
    void validarUsuarioNuncaComparaContrasenaEnTextoPlano() {
        // Si el hash almacenado fuese, por error, la propia contrasena en claro,
        // la validacion solo debe pasar via el PasswordEncoder, nunca con equals().
        Usuario usuarioBD = new Usuario();
        usuarioBD.setUsuario("admin");
        usuarioBD.setPassword("admin");
        usuarioBD.setRol("SUPER");

        when(usuarioDao.obtenerPorUsuario("admin")).thenReturn(usuarioBD);
        when(passwordEncoder.matches(eq("admin"), eq("admin"))).thenReturn(false);

        UsuarioDTO resultado = usuarioService.validarUsuario("admin", "admin");

        assertThat(resultado).isNull();
    }
}
