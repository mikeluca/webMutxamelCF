package com.mikedev.mutxamelcf.mvc.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

@Controller
public class FamiliarController {

    private static final Logger logger = LoggerFactory.getLogger(FamiliarController.class);

    @Autowired
    private FamiliarService familiarService;

    @Autowired
    private FamiliarJugadorService familiarJugadorService;

    @Autowired
    private JugadorService jugadorService;

    private static final List<String> PATROCINADORES = Arrays.asList(
            "patrocinador1.jpg", "patrocinador2.jpg", "patrocinador3.jpg",
            "patrocinador4.jpg", "patrocinador5.jpg", "patrocinador6.jpg",
            "patrocinador7.jpg", "patrocinador8.jpg");

    /**
     * Mostrar listado de familiares
     */
    @GetMapping("/admin/familiares")
    public String listarFamiliares(Model model) {
        logger.debug("Inicio listarFamiliares");
        List<FamiliarDTO> familiares = familiarService.obtenerTodos();
        List<JugadorDTO> jugadores = jugadorService.obtenerTodos();

        model.addAttribute("familiares", familiares);
        model.addAttribute("listaJugadores", jugadores);
        model.addAttribute("patrocinadores", PATROCINADORES);

        logger.debug("Fin listarFamiliares: totalFamiliares={}, totalJugadores={}",
                familiares.size(), jugadores.size());
        return "admin/familiares";
    }

    /**
     * Listado JSON de todos los familiares (para selects dinámicos).
     */
    @GetMapping("/admin/familiares/todos")
    @ResponseBody
    public ResponseEntity<List<FamiliarDTO>> obtenerTodosFamiliaresJson() {
        logger.debug("Inicio obtenerTodosFamiliaresJson");
        List<FamiliarDTO> familiares = familiarService.obtenerTodos();
        logger.debug("Fin obtenerTodosFamiliaresJson: total={}", familiares.size());
        return ResponseEntity.ok(familiares);
    }

    /**
     * Listado JSON de todos los jugadores (para selects dinámicos).
     */
    @GetMapping("/admin/jugadores/todos-json")
    @ResponseBody
    public ResponseEntity<List<JugadorDTO>> obtenerTodosJugadoresJson() {
        logger.debug("Inicio obtenerTodosJugadoresJson");
        List<JugadorDTO> jugadores = jugadorService.obtenerTodos();
        logger.debug("Fin obtenerTodosJugadoresJson: total={}", jugadores.size());
        return ResponseEntity.ok(jugadores);
    }

    /**
     * Obtener un familiar por ID.
     */
    @GetMapping("/admin/familiares/{id}")
    @ResponseBody
    public ResponseEntity<FamiliarDTO> obtenerFamiliar(@PathVariable Long id) {
        logger.debug("Inicio obtenerFamiliar: id={}", id);
        FamiliarDTO familiar = familiarService.obtenerFamiliarPorId(id);
        if (familiar == null) {
            logger.warn("Familiar no encontrado: id={}", id);
            return ResponseEntity.notFound().build();
        }
        logger.debug("Fin obtenerFamiliar: id={}, encontrado=true", id);
        return ResponseEntity.ok(familiar);
    }

    /**
     * Obtener los jugadores asociados a un familiar por su ID.
     */
    @GetMapping("/admin/familiares/{id}/jugadores")
    @ResponseBody
    public ResponseEntity<List<FamiliarJugadorDTO>> obtenerJugadoresDeFamiliar(@PathVariable Long id) {
        logger.debug("Inicio obtenerJugadoresDeFamiliar: familiarId={}", id);
        List<FamiliarJugadorDTO> relaciones = familiarJugadorService.obtenerJugadoresDeFamiliar(id);
        logger.debug("Fin obtenerJugadoresDeFamiliar: familiarId={}, total={}", id, relaciones.size());
        return ResponseEntity.ok(relaciones);
    }

    /**
     * Obtener los familiares asociados a un jugador por su ID.
     */
    @GetMapping("/admin/jugadores/{id}/familiares")
    @ResponseBody
    public ResponseEntity<List<FamiliarJugadorDTO>> obtenerFamiliaresDeJugador(@PathVariable Long id) {
        logger.debug("Inicio obtenerFamiliaresDeJugador: jugadorId={}", id);
        List<FamiliarJugadorDTO> relaciones = familiarJugadorService.obtenerFamiliaresDeJugador(id);
        logger.debug("Fin obtenerFamiliaresDeJugador: jugadorId={}, total={}", id, relaciones.size());
        return ResponseEntity.ok(relaciones);
    }

    /**
     * Guardar un familiar nuevo o modificar uno existente.
     */
    @PostMapping("/admin/familiares/guardar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarFamiliar(@ModelAttribute FamiliarDTO familiar) {
        logger.debug("Inicio guardarFamiliar: id={}, nombre={}", familiar.getId(), familiar.getNombre());
        Map<String, Object> response = new HashMap<>();

        try {
            if (familiar.getTelefono() == null || familiar.getTelefono().isBlank()
                    || familiar.getEmail() == null || familiar.getEmail().isBlank()) {
                response.put("error", "El teléfono y el email son obligatorios.");
                logger.warn("Guardar familiar rechazado por datos de contacto incompletos: id={}", familiar.getId());
                logger.debug("Fin guardarFamiliar: resultado=DATOS_INVALIDOS");
                return ResponseEntity.badRequest().body(response);
            }
            if (familiar.getRecibeInfoClub() == null) {
                familiar.setRecibeInfoClub(0);
            }
            if (familiar.getWhatsappActivo() == null) {
                familiar.setWhatsappActivo(0);
            }

            boolean resultado = familiarService.guardarFamiliar(familiar);

            if (resultado) {
                response.put("mensaje", familiar.getId() == null
                        ? "Familiar creado correctamente."
                        : "Familiar actualizado correctamente.");
                response.put("familiar", familiar);
                logger.debug("Fin guardarFamiliar: resultado=OK");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "No se ha podido guardar el familiar.");
                logger.warn("guardarFamiliar fallido: nombre={}", familiar.getNombre());
                logger.debug("Fin guardarFamiliar: resultado=FALLIDO");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error al guardar el familiar: {}", e.getMessage(), e);
            response.put("error", "Error al guardar el familiar.");
            logger.debug("Fin guardarFamiliar: resultado=ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Eliminar un familiar.
     */
    @PostMapping("/admin/familiares/borrar/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> borrarFamiliar(@PathVariable Long id) {
        logger.debug("Inicio borrarFamiliar: id={}", id);
        Map<String, String> response = new HashMap<>();

        try {
            familiarService.eliminarFamiliar(id);
            response.put("mensaje", "Familiar eliminado correctamente.");
            logger.debug("Fin borrarFamiliar: id={}, eliminado=true", id);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            logger.warn("No se puede eliminar el familiar id={}: {}", id, e.getMessage());
            response.put("error", e.getMessage());
            logger.debug("Fin borrarFamiliar: id={}, resultado=RELACIONES_EXISTENTES", id);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error al eliminar el familiar id={}: {}", id, e.getMessage(), e);
            response.put("error", "No se ha podido eliminar el familiar.");
            logger.debug("Fin borrarFamiliar: id={}, resultado=ERROR", id);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Asignar relación Familiar - Jugador.
     */
    @PostMapping("/admin/familiar-jugador/asignar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> asignarFamiliarJugador(@ModelAttribute FamiliarJugadorDTO relacion) {
        logger.debug("Inicio asignarFamiliarJugador: familiarId={}, jugadorId={}, parentesco={}, principal={}",
                relacion.getFamiliarId(), relacion.getJugadorId(), relacion.getParentesco(), relacion.getEsPrincipal());
        Map<String, String> response = new HashMap<>();

        try {
            if (relacion.getFamiliarId() == null || relacion.getJugadorId() == null) {
                response.put("error", "Debe seleccionar un familiar y un jugador.");
                logger.debug("Fin asignarFamiliarJugador: resultado=DATOS_INVALIDOS");
                return ResponseEntity.badRequest().body(response);
            }

            if (relacion.getEsPrincipal() == null) {
                relacion.setEsPrincipal(0);
            }

            boolean guardado = familiarJugadorService.guardarFamiliarJugador(relacion);

            if (guardado) {
                response.put("mensaje", "Relación familiar-jugador guardada correctamente.");
                logger.debug("Fin asignarFamiliarJugador: resultado=OK");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "La relación entre este familiar y jugador ya existe.");
                logger.warn("asignarFamiliarJugador duplicado: familiarId={}, jugadorId={}",
                        relacion.getFamiliarId(), relacion.getJugadorId());
                logger.debug("Fin asignarFamiliarJugador: resultado=DUPLICADO");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error al asignar familiar a jugador: {}", e.getMessage(), e);
            response.put("error", "Error al guardar la relación familiar-jugador.");
            logger.debug("Fin asignarFamiliarJugador: resultado=ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Marca un familiar como contacto principal del jugador asociado.
     */
    @PostMapping("/admin/familiar-jugador/principal/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> marcarFamiliarPrincipal(@PathVariable Long id) {
        logger.debug("Inicio marcarFamiliarPrincipal: relacionId={}", id);
        Map<String, String> response = new HashMap<>();

        try {
            FamiliarJugadorDTO relacion = familiarJugadorService.obtenerPorId(id);
            if (relacion == null) {
                response.put("error", "La relación familiar-jugador no existe.");
                logger.debug("Fin marcarFamiliarPrincipal: relacionId={}, resultado=NO_ENCONTRADA", id);
                return ResponseEntity.notFound().build();
            }

            relacion.setEsPrincipal(1);
            if (!familiarJugadorService.guardarFamiliarJugador(relacion)) {
                response.put("error", "No se ha podido actualizar el familiar principal.");
                logger.debug("Fin marcarFamiliarPrincipal: relacionId={}, resultado=FALLIDO", id);
                return ResponseEntity.badRequest().body(response);
            }

            response.put("mensaje", "Familiar principal actualizado correctamente.");
            logger.debug("Fin marcarFamiliarPrincipal: relacionId={}, resultado=OK", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error al actualizar familiar principal relacionId={}: {}", id, e.getMessage(), e);
            response.put("error", "Error al actualizar el familiar principal.");
            logger.debug("Fin marcarFamiliarPrincipal: relacionId={}, resultado=ERROR", id);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Desasignar (eliminar) relación Familiar - Jugador.
     */
    @PostMapping("/admin/familiar-jugador/desasignar/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> desasignarFamiliarJugador(@PathVariable Long id) {
        logger.debug("Inicio desasignarFamiliarJugador: id={}", id);
        Map<String, String> response = new HashMap<>();

        try {
            familiarJugadorService.eliminar(id);
            response.put("mensaje", "Relación eliminada correctamente.");
            logger.debug("Fin desasignarFamiliarJugador: id={}, eliminado=true", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error al desasignar familiar-jugador id={}: {}", id, e.getMessage(), e);
            response.put("error", "Error al eliminar la relación.");
            logger.debug("Fin desasignarFamiliarJugador: id={}, resultado=ERROR", id);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}