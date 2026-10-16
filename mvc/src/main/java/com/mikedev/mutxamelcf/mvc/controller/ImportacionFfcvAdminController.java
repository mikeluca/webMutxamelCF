package com.mikedev.mutxamelcf.mvc.controller;

import java.io.IOException;
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

import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;
import com.mikedev.mutxamelcf.service.ImportacionFfcvService;

/**
 * Importación semanal de partidos desde el CSV de la intranet de la FFCV.
 * Solo SUPER y ADMIN (ver SecurityConfig). El CSRF es el del resto del
 * panel: cabecera X-CSRF-TOKEN en cada POST.
 */
@RestController
@RequestMapping("/admin/ffcv")
public class ImportacionFfcvAdminController {

    private static final Logger logger = LoggerFactory.getLogger(ImportacionFfcvAdminController.class);

    /** El CSV real ocupa unos pocos KB; esto solo evita archivos absurdos. */
    static final long TAMANO_MAXIMO_BYTES = 1024 * 1024;

    private final ImportacionFfcvService importacionFfcvService;

    public ImportacionFfcvAdminController(ImportacionFfcvService importacionFfcvService) {
        this.importacionFfcvService = importacionFfcvService;
    }

    @PostMapping("/previsualizar")
    public ResponseEntity<?> previsualizar(@RequestParam("archivo") MultipartFile archivo) {
        logger.debug("Inicio previsualizar");

        if (archivo == null || archivo.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Selecciona el archivo LstPartidos.csv");
        }

        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            return error(HttpStatus.BAD_REQUEST, "El archivo es demasiado grande para ser un listado de partidos");
        }

        try {

            PrevisualizacionFfcv previsualizacion = importacionFfcvService.previsualizar(archivo.getBytes());

            logger.debug("Fin previsualizar: filas={}", previsualizacion.filas().size());

            return ResponseEntity.ok(previsualizacion);

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el archivo subido: {}", e.getMessage());
            return error(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");

        } catch (Exception e) {

            logger.error("Error al previsualizar la importación FFCV: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al procesar el archivo");
        }
    }

    @PostMapping("/confirmar")
    public ResponseEntity<?> confirmar(@RequestBody ConfirmarFfcvRequest request, Authentication authentication) {
        logger.debug("Inicio confirmar");

        if (request == null || request.previsualizacion() == null) {
            return error(HttpStatus.BAD_REQUEST, "No hay nada que confirmar");
        }

        try {

            String usuario = authentication != null ? authentication.getName() : "desconocido";

            ResultadoImportacionFfcv resultado = importacionFfcvService.confirmar(
                    usuario, request.previsualizacion(), request.asignaciones());

            logger.debug("Fin confirmar");

            return ResponseEntity.ok(resultado);

        } catch (IllegalArgumentException e) {

            return error(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            logger.error("Error al confirmar la importación FFCV: {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la importación");
        }
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }

}
