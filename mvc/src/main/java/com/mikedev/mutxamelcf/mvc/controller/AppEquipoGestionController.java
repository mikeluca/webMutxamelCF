package com.mikedev.mutxamelcf.mvc.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorEquipoAppDTO;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.model.FamiliarContactoDTO;
import com.mikedev.mutxamelcf.service.FamiliarService;

@RestController
@RequestMapping("/api/app/equipos/jugadores")
public class AppEquipoGestionController {

        private final EquipoGestionDao equipoGestionDao;
        private final JugadorService jugadorService;
        private final FamiliarService familiarService;
        private final JustificacionFaltaPartidoDao justificacionFaltaPartidoDao;
        private final PartidoDao partidoDao;

        public AppEquipoGestionController(
                        EquipoGestionDao equipoGestionDao,
                        JugadorService jugadorService,
                        FamiliarService familiarService,
                        JustificacionFaltaPartidoDao justificacionFaltaPartidoDao,
                        PartidoDao partidoDao) {

                this.justificacionFaltaPartidoDao = justificacionFaltaPartidoDao;
                this.partidoDao = partidoDao;

                this.equipoGestionDao = equipoGestionDao;
                this.jugadorService = jugadorService;
                this.familiarService = familiarService;
        }

        /**
         * Obtener los jugadores de un equipo que el usuario puede gestionar.
         *
         * GET /api/app/equipos/jugadores?equipoId=...&partidoId=... (opcional)
         *
         * Con partidoId, cada jugador lleva ausenciaJustificada y
         * motivoAusencia para ese partido (formulario de convocatoria).
         */
        @GetMapping
        public ResponseEntity<?> obtenerJugadores(
                        @RequestParam Long equipoId,
                        @RequestParam(required = false) Long partidoId,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.valueOf(
                                        authentication.getName());

                        if (equipoId == null || equipoId <= 0) {
                                return ResponseEntity
                                                .badRequest()
                                                .body("El ID del equipo no es válido");
                        }

                        if (!equipoGestionDao.existeEquipo(equipoId)) {
                                return ResponseEntity
                                                .badRequest()
                                                .body("El equipo no existe");
                        }

                        if (!equipoGestionDao.puedeGestionarEquipo(
                                        usuarioId,
                                        equipoId)) {

                                return ResponseEntity
                                                .status(HttpStatus.FORBIDDEN)
                                                .body("No tienes permiso para consultar este equipo");
                        }

                        if (partidoId != null) {

                                Partido partido = partidoDao.obtenerPorId(partidoId);

                                if (partido == null || !equipoId.equals(partido.getEquipoId())) {
                                        return ResponseEntity
                                                        .badRequest()
                                                        .body("El partido no pertenece al equipo");
                                }
                        }

                        List<Long> jugadoresIds = equipoGestionDao.obtenerJugadoresPorEquipo(equipoId);

                        // SEC-10: se expone JugadorEquipoAppDTO (proyección mínima), no
                        // JugadorDTO completo -- la app de gestión no necesita fecha
                        // de nacimiento, nacionalidad, población ni la foto en byte[].
                        List<JugadorEquipoAppDTO> jugadores = new ArrayList<>();

                        Map<Long, JustificacionFaltaPartido> ausencias = new HashMap<>();

                        if (partidoId != null) {
                                for (JustificacionFaltaPartido j : justificacionFaltaPartidoDao.obtenerPorPartido(partidoId)) {
                                        ausencias.put(j.getJugadorId(), j);
                                }
                        }

                        for (Long jugadorId : jugadoresIds) {

                                JugadorDTO jugador = jugadorService.obtenerJugadorPorId(jugadorId);

                                if (jugador != null) {
                                        JugadorEquipoAppDTO dto = JugadorEquipoAppDTO.desde(jugador);
                                        JustificacionFaltaPartido ausencia = ausencias.get(jugadorId);

                                        if (ausencia != null) {
                                                dto.setAusenciaJustificada(true);
                                                dto.setMotivoAusencia(ausencia.getMotivo());
                                        }

                                        jugadores.add(dto);
                                }
                        }

                        return ResponseEntity.ok(jugadores);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener los jugadores del equipo");
                }
        }

        @GetMapping("/{jugadorId}/familiares")
        public ResponseEntity<?> obtenerFamiliares(
                        @PathVariable Long jugadorId,
                        @RequestParam Long equipoId,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.valueOf(authentication.getName());

                        if (jugadorId == null || jugadorId <= 0) {
                                return ResponseEntity.badRequest()
                                                .body("El ID del jugador no es válido");
                        }

                        if (equipoId == null || equipoId <= 0) {
                                return ResponseEntity.badRequest()
                                                .body("El ID del equipo no es válido");
                        }

                        /*
                         * El equipo debe existir.
                         */
                        if (!equipoGestionDao.existeEquipo(equipoId)) {
                                return ResponseEntity.badRequest()
                                                .body("El equipo no existe");
                        }

                        /*
                         * El usuario debe poder gestionar el equipo.
                         */
                        if (!equipoGestionDao.puedeGestionarEquipo(
                                        usuarioId,
                                        equipoId)) {

                                return ResponseEntity
                                                .status(HttpStatus.FORBIDDEN)
                                                .body(
                                                                "No tienes permiso para gestionar " +
                                                                                "este equipo");
                        }

                        /*
                         * El jugador debe pertenecer al equipo.
                         */
                        if (!equipoGestionDao.perteneceJugadorAEquipo(
                                        jugadorId,
                                        equipoId)) {

                                return ResponseEntity
                                                .status(HttpStatus.FORBIDDEN)
                                                .body(
                                                                "El jugador no pertenece a este equipo");
                        }

                        /*
                         * El Service se encarga de obtener los familiares
                         * y convertir Familiar -> FamiliarContactoDTO.
                         */
                        List<FamiliarContactoDTO> familiares = familiarService.obtenerFamiliaresPorJugador(
                                        jugadorId);

                        return ResponseEntity.ok(familiares);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(
                                                        "Error al obtener los familiares " +
                                                                        "del jugador");
                }
        }
}