package com.mikedev.mutxamelcf.mvc.api;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.PrevisualizacionVeteranos;
import com.mikedev.mutxamelcf.model.ResultadoImportacionVeteranos;
import com.mikedev.mutxamelcf.mvc.controller.ImportacionVeteranosAdminController;
import com.mikedev.mutxamelcf.service.ImportacionVeteranosService;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;
import com.mikedev.mutxamelcf.mvc.controller.ConfirmarFfcvRequest;
import com.mikedev.mutxamelcf.mvc.controller.ImportacionClasificacionesAdminController;
import com.mikedev.mutxamelcf.mvc.controller.ImportacionFfcvAdminController;
import com.mikedev.mutxamelcf.service.ImportacionClasificacionesService;
import com.mikedev.mutxamelcf.service.ImportacionFfcvService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Subir los datos de la FFCV (partidos y clasificaciones) desde la app. Solo ADMIN_APP (403 al resto). Envuelve los
 * mismos servicios que el panel web (ImportacionFfcvService e ImportacionClasificacionesService), con los mismos tipos
 * de archivo, tamaños máximos y mensajes. Los errores van como {"error": "..."}.
 */
@RestController
@RequestMapping("/api/app/admin/ffcv")
public class AppAdminFfcvController {

    private static final Logger logger = LoggerFactory.getLogger(AppAdminFfcvController.class);

    private static final String SOLO_ADMIN = "Solo un administrador puede importar los datos de la FFCV";

    /** Cuerpo de POST /clasificaciones/confirmar: los equipos que se confirman (como los equipoId del panel). */
    public record ConfirmarClasificacionesRequest(List<Long> equipoIds) {
    }

    private final ImportacionFfcvService importacionFfcvService;
    private final ImportacionClasificacionesService importacionClasificacionesService;
    private final UsuarioAppService usuarioAppService;
    private final PrevisualizacionesClasificacionesApp previas;
    private final ImportacionVeteranosService importacionVeteranosService;
    private final PrevisualizacionesVeteranosApp previasVeteranos;

    public AppAdminFfcvController(
            ImportacionFfcvService importacionFfcvService,
            ImportacionClasificacionesService importacionClasificacionesService,
            UsuarioAppService usuarioAppService,
            PrevisualizacionesClasificacionesApp previas,
            ImportacionVeteranosService importacionVeteranosService,
            PrevisualizacionesVeteranosApp previasVeteranos) {

        this.importacionFfcvService = importacionFfcvService;
        this.importacionClasificacionesService = importacionClasificacionesService;
        this.usuarioAppService = usuarioAppService;
        this.previas = previas;
        this.importacionVeteranosService = importacionVeteranosService;
        this.previasVeteranos = previasVeteranos;
    }

    // ------------------------------------------------------------------
    // Clasificación de veteranos (LVCA)
    // ------------------------------------------------------------------

    /**
     * POST /api/app/admin/ffcv/veteranos/previsualizar (multipart, campo "archivo"): la página
     * https://ligaveteranosalicante.es/scoretable/ guardada desde el navegador (.html). La vista previa se guarda en el
     * servidor (una por administrador, 30 minutos) y es la que se confirma.
     */
    @PostMapping("/veteranos/previsualizar")
    public ResponseEntity<?> previsualizarVeteranos(@RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {

        Integer usuarioId = usuarioId(authentication);

        if (usuarioId == null) {
            return error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        try {

            usuarioAppService.exigirAdminApp(usuarioId, SOLO_ADMIN);

            // Una vista previa nueva sustituye siempre a la anterior.
            previasVeteranos.descartar(usuarioId);

            String invalido = ImportacionVeteranosAdminController.validarArchivo(archivo);

            if (invalido != null) {
                return error(HttpStatus.BAD_REQUEST, invalido);
            }

            PrevisualizacionVeteranos previa = importacionVeteranosService.previsualizar(archivo.getBytes());

            previasVeteranos.guardar(usuarioId, previa);

            return ResponseEntity.ok(previa);

        } catch (SecurityException e) {

            return error(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el archivo subido: {}", e.getMessage());
            return error(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");

        } catch (Exception e) {

            logger.error("Error al previsualizar la clasificación de veteranos desde la app: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al procesar el archivo");
        }
    }

    /** POST /api/app/admin/ffcv/veteranos/confirmar (sin cuerpo): confirma la vista previa guardada. */
    @PostMapping("/veteranos/confirmar")
    public ResponseEntity<?> confirmarVeteranos(Authentication authentication) {

        Integer usuarioId = usuarioId(authentication);

        if (usuarioId == null) {
            return error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        try {

            usuarioAppService.exigirAdminApp(usuarioId, SOLO_ADMIN);

            PrevisualizacionVeteranos previa = previasVeteranos.vigente(usuarioId);

            if (previa == null) {
                return error(HttpStatus.BAD_REQUEST, "La vista previa ha caducado: vuelve a subir el archivo");
            }

            ResultadoImportacionVeteranos resultado = importacionVeteranosService.confirmar(authentication.getName(),
                    previa);

            // Importada: no se puede confirmar dos veces la misma vista previa.
            previasVeteranos.descartar(usuarioId);

            return ResponseEntity.ok(resultado);

        } catch (SecurityException e) {

            return error(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            logger.error("Error al confirmar la clasificación de veteranos desde la app: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la importación");
        }
    }

    // ------------------------------------------------------------------
    // Partidos
    // ------------------------------------------------------------------

    /** POST /api/app/admin/ffcv/partidos/previsualizar (multipart, campo "archivo"): LstPartidos.csv. */
    @PostMapping("/partidos/previsualizar")
    public ResponseEntity<?> previsualizarPartidos(@RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {

        Integer usuarioId = usuarioId(authentication);

        if (usuarioId == null) {
            return error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        try {

            usuarioAppService.exigirAdminApp(usuarioId, SOLO_ADMIN);

            if (archivo == null || archivo.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "Selecciona el archivo LstPartidos.csv");
            }

            if (archivo.getSize() > ImportacionFfcvAdminController.TAMANO_MAXIMO_BYTES) {
                return error(HttpStatus.BAD_REQUEST, "El archivo es demasiado grande para ser un listado de partidos");
            }

            PrevisualizacionFfcv previsualizacion = importacionFfcvService.previsualizar(archivo.getBytes());

            return ResponseEntity.ok(previsualizacion);

        } catch (SecurityException e) {

            return error(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el archivo subido: {}", e.getMessage());
            return error(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");

        } catch (Exception e) {

            logger.error("Error al previsualizar la importación FFCV desde la app: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al procesar el archivo");
        }
    }

    /** POST /api/app/admin/ffcv/partidos/confirmar: el mismo cuerpo que el panel (previsualizacion + asignaciones). */
    @PostMapping("/partidos/confirmar")
    public ResponseEntity<?> confirmarPartidos(@RequestBody(required = false) ConfirmarFfcvRequest request,
            Authentication authentication) {

        Integer usuarioId = usuarioId(authentication);

        if (usuarioId == null) {
            return error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        try {

            usuarioAppService.exigirAdminApp(usuarioId, SOLO_ADMIN);

            if (request == null || request.previsualizacion() == null) {
                return error(HttpStatus.BAD_REQUEST, "No hay nada que confirmar");
            }

            ResultadoImportacionFfcv resultado = importacionFfcvService.confirmar(
                    authentication.getName(), request.previsualizacion(), request.asignaciones());

            return ResponseEntity.ok(resultado);

        } catch (SecurityException e) {

            return error(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            logger.error("Error al confirmar la importación FFCV desde la app: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la importación");
        }
    }

    // ------------------------------------------------------------------
    // Clasificaciones
    // ------------------------------------------------------------------

    /**
     * POST /api/app/admin/ffcv/clasificaciones/previsualizar (multipart, campo "archivo"): ListadoClasificaciones.xlsx.
     * La vista previa se guarda en el servidor (una por administrador, 30 minutos) y es la que se confirma.
     */
    @PostMapping("/clasificaciones/previsualizar")
    public ResponseEntity<?> previsualizarClasificaciones(@RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {

        Integer usuarioId = usuarioId(authentication);

        if (usuarioId == null) {
            return error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        try {

            usuarioAppService.exigirAdminApp(usuarioId, SOLO_ADMIN);

            // Una vista previa nueva sustituye siempre a la anterior.
            previas.descartar(usuarioId);

            if (archivo == null || archivo.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "Selecciona el archivo ListadoClasificaciones.xlsx");
            }

            String nombre = archivo.getOriginalFilename();

            if (nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                return error(HttpStatus.BAD_REQUEST,
                        "El archivo debe ser un Excel .xlsx (ListadoClasificaciones.xlsx)");
            }

            if (archivo.getSize() > ImportacionClasificacionesAdminController.TAMANO_MAXIMO_BYTES) {
                return error(HttpStatus.BAD_REQUEST,
                        "El archivo es demasiado grande para ser un listado de clasificaciones");
            }

            PrevisualizacionClasificaciones previa = importacionClasificacionesService.previsualizar(archivo.getBytes());

            previas.guardar(usuarioId, previa);

            return ResponseEntity.ok(previa);

        } catch (SecurityException e) {

            return error(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el archivo subido: {}", e.getMessage());
            return error(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");

        } catch (Exception e) {

            logger.error("Error al previsualizar la importación de clasificaciones desde la app: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al procesar el archivo");
        }
    }

    /**
     * POST /api/app/admin/ffcv/clasificaciones/confirmar {"equipoIds": [...]}: confirma la vista previa guardada
     * (la subida con /previsualizar). 400 si ha caducado o no hay.
     */
    @PostMapping("/clasificaciones/confirmar")
    public ResponseEntity<?> confirmarClasificaciones(
            @RequestBody(required = false) ConfirmarClasificacionesRequest request, Authentication authentication) {

        Integer usuarioId = usuarioId(authentication);

        if (usuarioId == null) {
            return error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        try {

            usuarioAppService.exigirAdminApp(usuarioId, SOLO_ADMIN);

            PrevisualizacionClasificaciones previa = previas.vigente(usuarioId);

            if (previa == null) {
                return error(HttpStatus.BAD_REQUEST, "La vista previa ha caducado: vuelve a subir el archivo");
            }

            ResultadoImportacionClasificaciones resultado = importacionClasificacionesService.confirmar(
                    authentication.getName(), previa, request == null ? null : request.equipoIds());

            // Importada: no se puede confirmar dos veces la misma vista previa.
            previas.descartar(usuarioId);

            return ResponseEntity.ok(resultado);

        } catch (SecurityException e) {

            return error(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (IllegalArgumentException e) {

            // La vista previa se conserva para poder corregir la selección.
            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            logger.error("Error al confirmar la importación de clasificaciones desde la app: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la importación");
        }
    }

    private static Integer usuarioId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        try {
            return Integer.parseInt(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }
}
