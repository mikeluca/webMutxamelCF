package com.mikedev.mutxamelcf.mvc.api;

import com.mikedev.mutxamelcf.model.TiendaPedidoItem;
import com.mikedev.mutxamelcf.model.TiendaPedidoRequest;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicTiendaControllerTest {

    private static TiendaPedidoRequest requestValido() {
        return new TiendaPedidoRequest("Ana", "600000000", "ana@example.com",
                List.of(new TiendaPedidoItem("Camiseta oficial", 2, List.of("M", "L"))));
    }

    @Test
    void crearPedidoDevuelve400SiElRequestEsNulo() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        ResponseEntity<Map<String, String>> response = controller.crearPedido(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(comunicacionesService, never()).enviarPedidoTienda(any(), any(), any());
    }

    @Test
    void crearPedidoDevuelve400SiNoHayItems() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com", List.of());

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearPedidoDevuelve400SiLaPrendaNoEsValida() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com",
                List.of(new TiendaPedidoItem("Prenda inventada", 1, List.of("M"))));

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(comunicacionesService, never()).enviarPedidoTienda(any(), any(), any());
    }

    @Test
    void crearPedidoDevuelve400SiLaTallaNoEsValida() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com",
                List.of(new TiendaPedidoItem("Camiseta oficial", 1, List.of("Talla-invalida"))));

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearPedidoDevuelve400SiFaltanElNombreOElEmail() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("", null, "",
                List.of(new TiendaPedidoItem("Camiseta oficial", 1, List.of("M"))));

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearPedidoDevuelve400SiLaCantidadEstaFueraDeRango() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com",
                List.of(new TiendaPedidoItem("Camiseta oficial", 25, List.of("M"))));

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(comunicacionesService, never()).enviarPedidoTienda(any(), any(), any());
    }

    @Test
    void crearPedidoDevuelve400SiLaCantidadEsNula() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com",
                List.of(new TiendaPedidoItem("Camiseta oficial", null, List.of("M"))));

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearPedidoDevuelve502SiFallaElEnvioDelEmail() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        when(comunicacionesService.enviarPedidoTienda(any(), any(), any())).thenReturn(false);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        ResponseEntity<Map<String, String>> response = controller.crearPedido(requestValido());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void crearPedidoDevuelve201YEnviaElEmailConDatosCorrectos() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        when(comunicacionesService.enviarPedidoTienda(any(), any(), any())).thenReturn(true);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        ResponseEntity<Map<String, String>> response = controller.crearPedido(requestValido());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("mensaje", "Pedido enviado correctamente");

        org.mockito.ArgumentCaptor<String> textoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(comunicacionesService).enviarPedidoTienda(org.mockito.ArgumentMatchers.eq("Ana"),
                org.mockito.ArgumentMatchers.eq("ana@example.com"), textoCaptor.capture());

        assertThat(textoCaptor.getValue()).contains("Nombre: Ana");
        assertThat(textoCaptor.getValue()).contains("Telefono: 600000000");
        assertThat(textoCaptor.getValue()).contains("- Camiseta oficial | Cantidad: 2 | Tallas: M, L");
    }

    @Test
    void crearPedidoUneLasTallasDeCadaItemConComas() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        when(comunicacionesService.enviarPedidoTienda(any(), any(), any())).thenReturn(true);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com",
                List.of(
                        new TiendaPedidoItem("Camiseta oficial", 2, List.of("M", "L")),
                        new TiendaPedidoItem("Segunda equipacion - colaboracion AECC", 1, List.of("S"))));

        controller.crearPedido(request);

        org.mockito.ArgumentCaptor<String> textoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(comunicacionesService).enviarPedidoTienda(anyString(), anyString(), textoCaptor.capture());

        assertThat(textoCaptor.getValue()).contains("- Camiseta oficial | Cantidad: 2 | Tallas: M, L");
        assertThat(textoCaptor.getValue())
                .contains("- Segunda equipacion - colaboracion AECC | Cantidad: 1 | Tallas: S");
    }

    @Test
    void crearPedidoAceptaTelefonoNuloYLoTrataComoVacio() {
        ComunicacionesService comunicacionesService = mock(ComunicacionesService.class);
        when(comunicacionesService.enviarPedidoTienda(any(), any(), any())).thenReturn(true);
        PublicTiendaController controller = new PublicTiendaController(comunicacionesService);

        TiendaPedidoRequest request = new TiendaPedidoRequest("Ana", null, "ana@example.com",
                List.of(new TiendaPedidoItem("Camiseta oficial", 1, List.of("M"))));

        ResponseEntity<Map<String, String>> response = controller.crearPedido(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
