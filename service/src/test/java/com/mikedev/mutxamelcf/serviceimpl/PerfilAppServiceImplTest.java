package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.PerfilAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@ExtendWith(MockitoExtension.class)
class PerfilAppServiceImplTest {

    @Mock
    private PerfilAppDao perfilAppDao;

    @Mock
    private UsuarioAppService usuarioAppService;

    private PerfilAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PerfilAppServiceImpl(perfilAppDao, usuarioAppService);
    }

    private static RolApp rol(String codigo) {
        RolApp rol = new RolApp();
        rol.setCodigo(codigo);
        return rol;
    }

    @Test
    void obtenerPerfilLanzaExcepcionSiElUsuarioNoExiste() {
        when(usuarioAppService.obtenerPorId(1)).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerPerfil(1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obtenerPerfilRellenaDatosDeFamiliarCuandoTieneEseRol() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);
        usuario.setEmail("ana@example.com");

        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of(rol("FAMILIAR")));

        Familiar familiar = new Familiar();
        familiar.setNombre("Ana");
        familiar.setApellidos("Garcia");
        familiar.setTelefono("600000000");
        when(perfilAppDao.obtenerFamiliarPorUsuario(1)).thenReturn(familiar);
        when(perfilAppDao.obtenerJugadoresPorUsuario(1)).thenReturn(List.of());

        PerfilAppResponse resultado = service.obtenerPerfil(1);

        assertThat(resultado.getNombre()).isEqualTo("Ana");
        assertThat(resultado.getTelefono()).isEqualTo("600000000");
    }

    @Test
    void obtenerPerfilRellenaDatosDelPrimerJugadorCuandoTieneEseRol() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);

        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of(rol("JUGADOR")));

        Jugador jugador = new Jugador();
        jugador.setNombre("Juan");
        jugador.setApellidos("Perez");
        when(perfilAppDao.obtenerJugadoresPorUsuario(1)).thenReturn(List.of(jugador));

        PerfilAppResponse resultado = service.obtenerPerfil(1);

        assertThat(resultado.getNombre()).isEqualTo("Juan");
        assertThat(resultado.getJugadores()).hasSize(1);
    }

    @Test
    void obtenerPerfilRellenaDatosDeEntrenadorYBorraElTelefono() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);

        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of(rol("ENTRENADOR")));
        when(perfilAppDao.obtenerJugadoresPorUsuario(1)).thenReturn(List.of());

        CuerpoTecnico miembro = new CuerpoTecnico();
        miembro.setNombre("Carlos");
        miembro.setApellidos("Ruiz");
        when(perfilAppDao.obtenerCuerpoTecnicoPorUsuario(1)).thenReturn(List.of(miembro));
        when(perfilAppDao.obtenerEquiposPorUsuario(1)).thenReturn(List.of(new Equipo()));

        PerfilAppResponse resultado = service.obtenerPerfil(1);

        assertThat(resultado.getNombre()).isEqualTo("Carlos");
        assertThat(resultado.getTelefono()).isNull();
        assertThat(resultado.getEquipos()).hasSize(1);
    }

    @Test
    void obtenerPerfilNoIncluyeEquiposSiNoTieneRolDeGestion() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);

        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of(rol("JUGADOR")));
        when(perfilAppDao.obtenerJugadoresPorUsuario(1)).thenReturn(List.of());

        PerfilAppResponse resultado = service.obtenerPerfil(1);

        assertThat(resultado.getEquipos()).isNullOrEmpty();
    }
}
