package com.mikedev.mutxamelcf.mvc.communication;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoTiendaHelperTest {

    // ---------- esPedidoValido ----------

    @Test
    void esPedidoValidoDevuelveFalseSiFaltaElNombre() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("", "ana@example.com",
                List.of("Camiseta oficial"), List.of("1"), List.of("M"));

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoDevuelveFalseSiElNombreEsNulo() {
        boolean valido = PedidoTiendaHelper.esPedidoValido(null, "ana@example.com",
                List.of("Camiseta oficial"), List.of("1"), List.of("M"));

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoDevuelveFalseSiFaltaElEmail() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "",
                List.of("Camiseta oficial"), List.of("1"), List.of("M"));

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoDevuelveFalseSiNoHayNingunaPrenda() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "ana@example.com",
                List.of(), List.of(), List.of());

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoDevuelveFalseSiLasListasNoTienenElMismoTamano() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "ana@example.com",
                List.of("Camiseta oficial"), List.of("1", "2"), List.of("M"));

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoDevuelveFalseSiLaPrendaNoEsValida() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "ana@example.com",
                List.of("Prenda inventada"), List.of("1"), List.of("M"));

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoDevuelveFalseSiLaTallaNoEsValida() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "ana@example.com",
                List.of("Camiseta oficial"), List.of("1"), List.of("Talla-invalida"));

        assertThat(valido).isFalse();
    }

    @Test
    void esPedidoValidoAceptaVariasTallasSeparadasPorComasEnUnaLinea() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "ana@example.com",
                List.of("Camiseta oficial"), List.of("2"), List.of("M, L"));

        assertThat(valido).isTrue();
    }

    @Test
    void esPedidoValidoDevuelveTrueConDatosCorrectos() {
        boolean valido = PedidoTiendaHelper.esPedidoValido("Ana", "ana@example.com",
                List.of("Camiseta oficial", "Segunda equipacion - colaboracion AECC"),
                List.of("1", "2"), List.of("M", "S, L"));

        assertThat(valido).isTrue();
    }

    // ---------- parsearCantidadesValidas ----------

    @Test
    void parsearCantidadesValidasDevuelveVacioSiNoEsUnNumero() {
        Optional<List<Integer>> resultado = PedidoTiendaHelper.parsearCantidadesValidas(List.of("no-numero"));

        assertThat(resultado).isEmpty();
    }

    @Test
    void parsearCantidadesValidasDevuelveVacioSiEstaFueraDeRango() {
        assertThat(PedidoTiendaHelper.parsearCantidadesValidas(List.of("0"))).isEmpty();
        assertThat(PedidoTiendaHelper.parsearCantidadesValidas(List.of("21"))).isEmpty();
    }

    @Test
    void parsearCantidadesValidasDevuelveLosEnterosCuandoSonValidos() {
        Optional<List<Integer>> resultado = PedidoTiendaHelper.parsearCantidadesValidas(List.of("1", "20"));

        assertThat(resultado).contains(List.of(1, 20));
    }

    // ---------- construirTextoPedido ----------

    @Test
    void construirTextoPedidoIncluyeLosDatosDelClienteYCadaLinea() {
        String texto = PedidoTiendaHelper.construirTextoPedido("Ana", "600000000", "ana@example.com",
                List.of("Camiseta oficial", "Segunda equipacion - colaboracion AECC"),
                List.of(2, 1), List.of("M, L", "S"));

        assertThat(texto).contains("Nombre: Ana");
        assertThat(texto).contains("Telefono: 600000000");
        assertThat(texto).contains("Email: ana@example.com");
        assertThat(texto).contains("- Camiseta oficial | Cantidad: 2 | Tallas: M, L");
        assertThat(texto).contains("- Segunda equipacion - colaboracion AECC | Cantidad: 1 | Tallas: S");
    }
}
