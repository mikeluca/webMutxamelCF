package com.mikedev.mutxamelcf.mvc.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.TiendaPedidoItem;
import com.mikedev.mutxamelcf.model.TiendaPedidoRequest;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.mvc.communication.PedidoTiendaHelper;

/**
 * Versión API (JSON, pública, sin autenticación) del mismo envío de
 * pedidos que ya hace el formulario web de /tienda
 * (MainController.crearPedido, POST /tienda/crear-pedido). Pensada para
 * que la app móvil pueda ofrecer la misma pantalla de Tienda.
 *
 * Reutiliza exactamente la misma validación y el mismo texto de pedido
 * que el formulario web a través de PedidoTiendaHelper, y el mismo envío
 * de email a través de ComunicacionesService.enviarPedidoTienda.
 */
@RestController
@RequestMapping("/api/public/tienda")
public class PublicTiendaController {

    private static final Logger logger = LoggerFactory.getLogger(PublicTiendaController.class);

    private final ComunicacionesService comunicacionesService;

    public PublicTiendaController(ComunicacionesService comunicacionesService) {
        this.comunicacionesService = comunicacionesService;
    }

    /**
     * Crear un pedido de tienda desde la app.
     *
     * POST /api/public/tienda/pedido
     */
    @PostMapping("/pedido")
    public ResponseEntity<Map<String, String>> crearPedido(@RequestBody(required = false) TiendaPedidoRequest request) {

        logger.debug("Inicio crearPedido (API): nombre={}",
                request == null ? null : request.getNombre());

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            logger.debug("Fin crearPedido (API): resultado=SIN_ITEMS");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", "Debe indicarse al menos una prenda"));
        }

        List<String> prendas = new ArrayList<>();
        List<String> cantidadesTexto = new ArrayList<>();
        List<String> tallasTexto = new ArrayList<>();

        for (TiendaPedidoItem item : request.getItems()) {
            prendas.add(item.getPrenda());
            cantidadesTexto.add(item.getCantidad() == null ? "0" : String.valueOf(item.getCantidad()));
            tallasTexto.add(item.getTallas() == null ? "" : String.join(", ", item.getTallas()));
        }

        String nombre = request.getNombre();
        String telefono = request.getTelefono() == null ? "" : request.getTelefono();
        String email = request.getEmail();

        if (!PedidoTiendaHelper.esPedidoValido(nombre, email, prendas, cantidadesTexto, tallasTexto)) {
            logger.warn("Pedido invalido recibido desde la app: nombre={}, email={}", nombre, email);
            logger.debug("Fin crearPedido (API): resultado=INVALIDO");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje",
                            "Revisa los datos del pedido: nombre, email, prenda o talla no válidos"));
        }

        Optional<List<Integer>> cantidadesValidadas = PedidoTiendaHelper.parsearCantidadesValidas(cantidadesTexto);

        if (cantidadesValidadas.isEmpty()) {
            logger.warn("Cantidades fuera de rango en el pedido desde la app: nombre={}", nombre);
            logger.debug("Fin crearPedido (API): resultado=CANTIDAD_INVALIDA");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", "Las cantidades deben ser números entre 1 y 20"));
        }

        String textoPedido = PedidoTiendaHelper.construirTextoPedido(
                nombre, telefono, email, prendas, cantidadesValidadas.get(), tallasTexto);

        if (!comunicacionesService.enviarPedidoTienda(nombre, email, textoPedido)) {
            logger.warn("No se pudo enviar el pedido por email desde la app: nombre={}", nombre);
            logger.debug("Fin crearPedido (API): resultado=ERROR_ENVIO");
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("mensaje", "No se pudo enviar el pedido, inténtalo de nuevo más tarde"));
        }

        logger.info("Pedido enviado por email correctamente desde la app: nombre={}", nombre);
        logger.debug("Fin crearPedido (API): resultado=OK");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("mensaje", "Pedido enviado correctamente"));
    }
}
