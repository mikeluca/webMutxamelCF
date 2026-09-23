package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.service.PerfilAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.PerfilAppResponse;
import com.mikedev.mutxamelcf.model.PerfilEquipoAppResponse;
import com.mikedev.mutxamelcf.model.PerfilJugadorAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;

@Service
public class PerfilAppServiceImpl implements PerfilAppService {

    private final PerfilAppDao perfilAppDao;
    private final UsuarioAppService usuarioAppService;

    public PerfilAppServiceImpl(
            PerfilAppDao perfilAppDao,
            UsuarioAppService usuarioAppService) {

        this.perfilAppDao = perfilAppDao;
        this.usuarioAppService = usuarioAppService;
    }

    @Override
    public PerfilAppResponse obtenerPerfil(int usuarioAppId) {

        UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioAppId);

        if (usuario == null) {
            throw new IllegalArgumentException(
                    "El usuario no existe.");
        }

        List<RolApp> roles = usuarioAppService.obtenerRoles(usuarioAppId);

        List<String> codigosRoles = roles.stream()
                .map(RolApp::getCodigo)
                .collect(Collectors.toList());

        PerfilAppResponse response = new PerfilAppResponse();

        response.setUsuarioId(usuario.getId());
        response.setEmail(usuario.getEmail());
        response.setRoles(codigosRoles);

        response.setFechaAlta(usuario.getFechaAlta());
        response.setFechaActivacion(
                usuario.getFechaActivacion());
        response.setFechaUltimoAcceso(
                usuario.getFechaUltimoAcceso());

        /*
         * ========================================================
         * FAMILIAR
         * ========================================================
         */

        if (codigosRoles.contains("FAMILIAR")) {

            Familiar familiar = perfilAppDao.obtenerFamiliarPorUsuario(
                    usuarioAppId);

            if (familiar != null) {

                response.setNombre(
                        familiar.getNombre());

                response.setApellidos(
                        familiar.getApellidos());

                response.setTelefono(
                        familiar.getTelefono());
            }
        }

        /*
         * ========================================================
         * JUGADOR
         * ========================================================
         */

        List<Jugador> jugadores = perfilAppDao.obtenerJugadoresPorUsuario(
                usuarioAppId);

        if (codigosRoles.contains("JUGADOR")
                && !jugadores.isEmpty()) {

            Jugador jugador = jugadores.get(0);

            response.setNombre(
                    jugador.getNombre());

            response.setApellidos(
                    jugador.getApellidos());
        }

        /*
         * ========================================================
         * ENTRENADOR
         * ========================================================
         */

        if (codigosRoles.contains("ENTRENADOR")) {

            List<CuerpoTecnico> cuerpoTecnico = perfilAppDao.obtenerCuerpoTecnicoPorUsuario(
                    usuarioAppId);

            if (!cuerpoTecnico.isEmpty()) {

                CuerpoTecnico miembro = cuerpoTecnico.get(0);

                response.setNombre(
                        miembro.getNombre());

                response.setApellidos(
                        miembro.getApellidos());

                response.setTelefono(null);
            }
        }

        /*
         * ========================================================
         * JUGADORES ASOCIADOS
         * ========================================================
         */

        List<PerfilJugadorAppResponse> jugadoresResponse = jugadores.stream()
                .map(this::mapJugador)
                .collect(Collectors.toList());

        response.setJugadores(jugadoresResponse);

        /*
         * ========================================================
         * EQUIPOS
         * ========================================================
         */

        if (codigosRoles.contains("ENTRENADOR")
                || codigosRoles.contains("COORDINADOR")
                || codigosRoles.contains("ADMIN_APP")) {

            List<Equipo> equipos = perfilAppDao.obtenerEquiposPorUsuario(
                    usuarioAppId);

            List<PerfilEquipoAppResponse> equiposResponse = equipos.stream()
                    .map(this::mapEquipo)
                    .collect(Collectors.toList());

            response.setEquipos(equiposResponse);
        }

        return response;
    }

    private PerfilJugadorAppResponse mapJugador(
            Jugador jugador) {

        PerfilJugadorAppResponse response = new PerfilJugadorAppResponse();

        response.setId(jugador.getId());
        response.setNombre(jugador.getNombre());
        response.setApellidos(jugador.getApellidos());
        response.setCategoria(jugador.getCategoria());
        response.setEquipo(jugador.getEquipo());
        response.setDeporte(jugador.getDeporte());
        response.setDorsal(jugador.getDorsal());
        response.setPosicion(jugador.getPosicion());

        return response;
    }

    private PerfilEquipoAppResponse mapEquipo(
            Equipo equipo) {

        PerfilEquipoAppResponse response = new PerfilEquipoAppResponse();

        response.setId(equipo.getId());
        response.setNombre(equipo.getNombre());
        response.setCategoria(equipo.getCategoria());
        response.setGrupo(equipo.getGrupo());
        response.setDeporte(equipo.getDeporte());

        return response;
    }
}