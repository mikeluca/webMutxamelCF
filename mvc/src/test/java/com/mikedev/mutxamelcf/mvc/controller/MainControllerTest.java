package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.PartidoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainControllerTest {

    private ComunicacionesService comunicacionesService;
    private JugadorService jugadoresService;
    private CuerpoTecnicoService cuerpoTecnicoService;
    private NoticiaService noticiaService;
    private PartidoService partidoService;
    private EquipoService equipoService;
    private MainController controller;

    @BeforeEach
    void setUp() {
        comunicacionesService = mock(ComunicacionesService.class);
        jugadoresService = mock(JugadorService.class);
        cuerpoTecnicoService = mock(CuerpoTecnicoService.class);
        noticiaService = mock(NoticiaService.class);
        partidoService = mock(PartidoService.class);
        equipoService = mock(EquipoService.class);
        controller = new MainController(comunicacionesService, jugadoresService, cuerpoTecnicoService, noticiaService,
                partidoService, equipoService);
    }

    @Test
    void sinParametroErrorNoHayMensaje() {
        Model model = new ExtendedModelMap();

        String vista = controller.login(null, model);

        assertEquals("login", vista);
        assertNull(model.getAttribute("errorMessage"));
    }

    @Test
    void errorTrueMuestraCredencialesIncorrectas() {
        Model model = new ExtendedModelMap();

        controller.login("true", model);

        assertEquals("Usuario o contraseña incorrectos", model.getAttribute("errorMessage"));
    }

    @Test
    void errorBloqueadoMuestraMensajeDeBloqueoPorRateLimit() {
        Model model = new ExtendedModelMap();

        controller.login("bloqueado", model);

        assertEquals(
                "Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos.",
                model.getAttribute("errorMessage"));
    }

    @Test
    void pantallaCargaDevuelveLaVistaSplash() {
        assertEquals("pantalla-carga", controller.pantallaCarga());
    }

    @Test
    void historiaRellenaPatrocinadores() {
        Model model = new ExtendedModelMap();
        assertEquals("historia", controller.historia(model));
        assertEquals(8, ((List<?>) model.getAttribute("patrocinadores")).size());
    }

    @Test
    void estadisticasPalmaresRellenaPatrocinadores() {
        Model model = new ExtendedModelMap();
        assertEquals("estadisticasPalmares", controller.estadisticasPalmares(model));
        assertTrue(((List<?>) model.getAttribute("patrocinadores")).size() > 0);
    }

    @Test
    void obraSocialRellenaPatrocinadores() {
        Model model = new ExtendedModelMap();
        assertEquals("obraSocial", controller.obraSocial(model));
    }

    @Test
    void inicioRellenaLasNoticias() {
        when(noticiaService.obtenerNoticiasParaMostrar()).thenReturn(List.of(new NoticiaDTO()));

        Model model = new ExtendedModelMap();
        assertEquals("index", controller.inicio(model));
        assertEquals(1, ((List<?>) model.getAttribute("noticias")).size());
    }

    @Test
    void contactoRellenaPatrocinadores() {
        Model model = new ExtendedModelMap();
        assertEquals("contacto", controller.contacto(model));
    }

    @Test
    void tiendaRellenaPatrocinadores() {
        Model model = new ExtendedModelMap();
        assertEquals("tienda", controller.tienda(model));
    }

    @Test
    void crearPedidoRedirigeConErrorSiFaltanDatosObligatorios() {
        String vista = controller.crearPedido("", "", "", null, null, null);

        assertEquals("redirect:/tienda?error=true", vista);
        verify(comunicacionesService, never()).enviarPedidoTienda(any(), any(), any());
    }

    @Test
    void crearPedidoRedirigeConErrorSiLaPrendaNoEsValida() {
        String vista = controller.crearPedido("Ana", "600000000", "ana@example.com",
                List.of("Prenda inventada"), List.of("1"), List.of("M"));

        assertEquals("redirect:/tienda?error=true", vista);
    }

    @Test
    void crearPedidoRedirigeConErrorSiLaTallaNoEsValida() {
        String vista = controller.crearPedido("Ana", "600000000", "ana@example.com",
                List.of("Camiseta oficial"), List.of("1"), List.of("Talla-invalida"));

        assertEquals("redirect:/tienda?error=true", vista);
    }

    @Test
    void crearPedidoRedirigeConErrorSiLaCantidadEstaFueraDeRango() {
        String vista = controller.crearPedido("Ana", "600000000", "ana@example.com",
                List.of("Camiseta oficial"), List.of("25"), List.of("M"));

        assertEquals("redirect:/tienda?error=true", vista);
    }

    @Test
    void crearPedidoRedirigeConErrorSiLaCantidadNoEsUnNumero() {
        String vista = controller.crearPedido("Ana", "600000000", "ana@example.com",
                List.of("Camiseta oficial"), List.of("no-numero"), List.of("M"));

        assertEquals("redirect:/tienda?error=true", vista);
    }

    @Test
    void crearPedidoRedirigeConErrorSiFallaElEnvioDelEmail() {
        when(comunicacionesService.enviarPedidoTienda(any(), any(), any())).thenReturn(false);

        String vista = controller.crearPedido("Ana", "600000000", "ana@example.com",
                List.of("Camiseta oficial"), List.of("1"), List.of("M"));

        assertEquals("redirect:/tienda?error=true", vista);
    }

    @Test
    void crearPedidoRedirigeConExitoCuandoTodoEsValido() {
        when(comunicacionesService.enviarPedidoTienda(any(), any(), any())).thenReturn(true);

        String vista = controller.crearPedido("Ana", "600000000", "ana@example.com",
                List.of("Camiseta oficial"), List.of("1"), List.of("M"));

        assertEquals("redirect:/tienda?pedido=ok", vista);
        verify(comunicacionesService).enviarPedidoTienda(anyString(), anyString(), anyString());
    }

    @Test
    void mostrarResultadosAgrupaLosResultadosPorCategoria() {
        ResultadoDTO senior1 = new ResultadoDTO();
        senior1.setCategoria("Senior");
        senior1.setEquipo("Senior A");

        ResultadoDTO senior2 = new ResultadoDTO();
        senior2.setCategoria("Senior");
        senior2.setEquipo("Senior B");

        ResultadoDTO juvenil = new ResultadoDTO();
        juvenil.setCategoria("Juvenil");
        juvenil.setEquipo("Juvenil A");

        when(partidoService.obtenerResultados("F")).thenReturn(List.of(senior1, senior2, juvenil));

        Model model = new ExtendedModelMap();
        assertEquals("resultados", controller.mostrarResultados(model));

        @SuppressWarnings("unchecked")
        Map<String, List<ResultadoDTO>> resultadosPorCategoria = (Map<String, List<ResultadoDTO>>) model
                .getAttribute("resultadosPorCategoria");

        assertEquals(List.of("Senior", "Juvenil"), List.copyOf(resultadosPorCategoria.keySet()));
        assertEquals(2, resultadosPorCategoria.get("Senior").size());
        assertEquals(1, resultadosPorCategoria.get("Juvenil").size());
    }

    @Test
    void categoriasDeEscuelitaMuestraElEquipoEscuelitaCuandoHayJugadores() {
        JugadorDTO jugador = new JugadorDTO();
        when(jugadoresService.obtenerJugadoresPorEquipo("Esc A")).thenReturn(List.of(jugador));

        Model model = new ExtendedModelMap();
        String vista = controller.categorias("Esc A", model);

        assertEquals("plantillasEscuelas", vista);
        assertEquals(jugador, model.getAttribute("equipoEscuelita"));
    }

    @Test
    void categoriasDeEscuelitaUsaJugadorVacioSiNoHayJugadores() {
        when(jugadoresService.obtenerJugadoresPorEquipo("Esc A")).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.categorias("Esc A", model);

        assertTrue(model.getAttribute("equipoEscuelita") instanceof JugadorDTO);
    }

    @Test
    void categoriasNormalesMuestraJugadoresYCuerpoTecnico() {
        when(jugadoresService.obtenerJugadoresPorEquipo("Senior")).thenReturn(List.of(new JugadorDTO()));
        when(cuerpoTecnicoService.obtenerCuerpoTecnicoPorEquipo("Senior")).thenReturn(List.of(new CuerpoTecnicoDTO()));

        Model model = new ExtendedModelMap();
        String vista = controller.categorias("Senior", model);

        assertEquals("plantilla", vista);
        assertEquals(1, ((List<?>) model.getAttribute("jugadores")).size());
        assertEquals(1, ((List<?>) model.getAttribute("staff")).size());
    }

    @Test
    void ampliarNoticiaRellenaLaNoticia() {
        NoticiaDTO noticia = new NoticiaDTO();
        when(noticiaService.obtenerNoticiaPorId(1)).thenReturn(noticia);

        Model model = new ExtendedModelMap();
        assertEquals("noticia", controller.ampliarNoticia(1, model));
        assertEquals(noticia, model.getAttribute("noticia"));
    }

    @Test
    void enviarEmailRedirigeAlIndiceCuandoTieneExito() {
        when(comunicacionesService.enviarMensajeContacto("Ana", "ana@example.com", "Hola")).thenReturn(true);

        assertEquals("redirect:/index", controller.enviarEmail("Ana", "ana@example.com", "Hola"));
    }

    @Test
    void enviarEmailRedirigeAlIndiceAunqueFalleElEnvio() {
        when(comunicacionesService.enviarMensajeContacto("Ana", "ana@example.com", "Hola")).thenReturn(false);

        assertEquals("redirect:/index", controller.enviarEmail("Ana", "ana@example.com", "Hola"));
    }

    @Test
    void mostrarEquiposPorCategoriaRellenaElMapaDeEquipos() {
        when(equipoService.obtenerEquiposAgrupadosPorCategoria("Futbol"))
                .thenReturn(Map.of("Senior", List.of(new EquipoDTO())));

        Model model = new ExtendedModelMap();
        assertEquals("listaEquipos", controller.mostrarEquiposPorCategoria("Futbol", model));
        assertEquals(1, ((Map<?, ?>) model.getAttribute("equiposPorCategoria")).size());
    }

    @Test
    void politicaPrivacidadRellenaPatrocinadores() {
        Model model = new ExtendedModelMap();
        assertEquals("politicaPrivacidad", controller.politicaPrivacidad(model));
    }

    @Test
    void todasNoticiasRellenaLaListaCompleta() {
        when(noticiaService.obtenerTodas()).thenReturn(List.of(new NoticiaDTO(), new NoticiaDTO()));

        Model model = new ExtendedModelMap();
        assertEquals("todasNoticias", controller.todasNoticias(model));
        assertEquals(2, ((List<?>) model.getAttribute("noticias")).size());
    }
}
