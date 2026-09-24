package com.mikedev.mutxamelcf.mvc.communication;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validación y construcción del texto del pedido de la Tienda,
 * compartidas entre el formulario web ({@code MainController.crearPedido},
 * POST /tienda/crear-pedido) y la API pública para la app
 * ({@code PublicTiendaController}, POST /api/public/tienda/pedido).
 *
 * Las tres listas (prendas/cantidades/tallas) van siempre "paralelas":
 * la posición i de cada una describe la misma línea del pedido. Las
 * tallas de cada línea llegan como una única cadena con las tallas de
 * cada unidad separadas por ", " (el formulario web ya las manda así
 * concatenadas; la API de la app las une antes de llamar aquí).
 */
public final class PedidoTiendaHelper {

    private static final Set<String> PRENDAS_VALIDAS = Set.of(
            "Camiseta oficial",
            "Segunda equipacion - colaboracion AECC");

    private static final Set<String> TALLAS_VALIDAS = Set.of(
            "2", "4", "6", "8", "10", "12", "14", "S", "M", "L", "XL", "XXL", "3XL", "4XL");

    private static final int CANTIDAD_MINIMA = 1;
    private static final int CANTIDAD_MAXIMA = 20;

    private PedidoTiendaHelper() {
    }

    /**
     * Valida que el pedido tenga los datos obligatorios y que las
     * prendas/tallas sean opciones permitidas. No valida el formato de
     * las cantidades (eso lo hace {@link #parsearCantidadesValidas}).
     */
    public static boolean esPedidoValido(
            String nombre,
            String email,
            List<String> prendas,
            List<String> cantidades,
            List<String> tallas) {

        if (nombre == null || nombre.isBlank()
                || email == null || email.isBlank()
                || prendas == null || cantidades == null || tallas == null
                || prendas.size() != cantidades.size() || cantidades.size() != tallas.size()
                || prendas.isEmpty()) {

            return false;
        }

        if (!prendas.stream().allMatch(PRENDAS_VALIDAS::contains)) {
            return false;
        }

        return tallas.stream()
                .flatMap(talla -> Arrays.stream(talla.split(",\\s*")))
                .allMatch(TALLAS_VALIDAS::contains);
    }

    /**
     * Convierte las cantidades (texto) a enteros, comprobando que todas
     * estén entre {@value #CANTIDAD_MINIMA} y {@value #CANTIDAD_MAXIMA}.
     * Devuelve {@link Optional#empty()} si alguna no es un número válido
     * o está fuera de rango.
     */
    public static Optional<List<Integer>> parsearCantidadesValidas(List<String> cantidades) {

        try {
            List<Integer> cantidadesValidadas = cantidades.stream()
                    .map(Integer::parseInt)
                    .filter(cantidad -> cantidad >= CANTIDAD_MINIMA && cantidad <= CANTIDAD_MAXIMA)
                    .collect(Collectors.toList());

            if (cantidadesValidadas.size() != cantidades.size()) {
                return Optional.empty();
            }

            return Optional.of(cantidadesValidadas);

        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    // Construye el texto del email de pedido a partir de los datos del formulario
    public static String construirTextoPedido(
            String nombre,
            String telefono,
            String email,
            List<String> prendas,
            List<Integer> cantidadesValidadas,
            List<String> tallas) {

        StringBuilder pedido = new StringBuilder("Datos del cliente\nNombre: ").append(nombre)
                .append("\nTelefono: ").append(telefono).append("\nEmail: ").append(email)
                .append("\n\nPrendas seleccionadas\n");

        for (int i = 0; i < prendas.size(); i++) {
            pedido.append("- ").append(prendas.get(i)).append(" | Cantidad: ")
                    .append(cantidadesValidadas.get(i)).append(" | Tallas: ").append(tallas.get(i)).append("\n");
        }

        return pedido.toString();
    }
}
