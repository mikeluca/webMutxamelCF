package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ImagenRedesDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.PartidoImagen;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

class ImagenesRedesServiceImplTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 13);
    private static final LocalDate DOMINGO = VIERNES.plusDays(2);

    private final ImagenRedesDao dao = mock(ImagenRedesDao.class);
    private final ImagenesRedesRenderer renderer = mock(ImagenesRedesRenderer.class);
    private final EquipoGestionDao equipoGestionDao = mock(EquipoGestionDao.class);
    private final NotificacionAppService notificaciones = mock(NotificacionAppService.class);
    private final FcmPushService push = mock(FcmPushService.class);
    private final UsuarioAppDao usuarioAppDao = mock(UsuarioAppDao.class);

    private ImagenesRedesServiceImpl servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-13T11:00:00Z"), ZoneId.of("Europe/Madrid"));

        servicio = new ImagenesRedesServiceImpl(dao, renderer, equipoGestionDao, notificaciones, push, reloj,
                new TextosAvisos(), new IdiomasUsuarios(usuarioAppDao), "Primer Equipo");

        when(equipoGestionDao.esCoordinadorOAdmin(7L)).thenReturn(true);
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(7L, 8L));
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of(8L, 9L));
        when(notificaciones.puedeRecibir(anyLong(), anyString())).thenReturn(true);
        when(usuarioAppDao.obtenerIdioma(7L)).thenReturn("ca");
        when(usuarioAppDao.obtenerIdioma(8L)).thenReturn("en");
        when(usuarioAppDao.obtenerIdioma(9L)).thenReturn("es");
        when(renderer.renderizar(any())).thenReturn(new byte[] { 1 });
        when(dao.versionMaxima(anyString(), any())).thenReturn(2);
        when(dao.partidosEntre(VIERNES, DOMINGO)).thenReturn(partidos(9));
    }

    private static List<PartidoImagen> partidos(int alevines) {

        List<PartidoImagen> lista = new ArrayList<>();
        lista.add(new PartidoImagen(1L, 1L, "Primer Equipo", "1", null, "Rival", null, VIERNES.plusDays(1), "17:00",
                "Campo", true, false, null, null, null));

        for (int i = 0; i < alevines; i++) {
            lista.add(new PartidoImagen(10L + i, 10L + i, "Alevín " + (char) ('A' + i), String.valueOf(20 + i), "F8",
                    "Rival B", null, VIERNES.plusDays(1), "10:00", "Campo", true, false, null, null, null));
        }

        return lista;
    }

    @Test
    void sustituyeLasImagenesDelFinDeSemanaYSubeLaVersion() {

        int generadas = servicio.generarYAvisar("PREVIA", VIERNES);

        // 9 partidos de F8 (el Primer Equipo está excluido) -> 2 páginas (5 + 4)
        assertThat(generadas).isEqualTo(2);
        verify(dao).borrar("PREVIA", VIERNES);
        verify(dao).insertar(eq("PREVIA"), eq("F8"), eq(1), eq(2), eq(VIERNES), eq(DOMINGO), any(), any(), eq(3));
        verify(dao).insertar(eq("PREVIA"), eq("F8"), eq(2), eq(2), eq(VIERNES), eq(DOMINGO), any(), any(), eq(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void avisaACoordinadoresYAdminsUnaVezCadaUnoYEnSuIdioma() {

        servicio.generarYAvisar("PREVIA", VIERNES);

        ArgumentCaptor<String> titulos = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> cuentas = ArgumentCaptor.forClass(Long.class);

        // La referencia es el viernes como yyyyMMdd
        verify(notificaciones, times(3)).crear(cuentas.capture(), eq("IMAGENES_REDES"), titulos.capture(), anyString(),
                eq(20261113L));
        assertThat(cuentas.getAllValues()).containsExactlyInAnyOrder(7L, 8L, 9L);
        assertThat(titulos.getAllValues()).containsExactlyInAnyOrder("Imatges del cap de setmana",
                "Weekend images", "Imágenes del fin de semana");

        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(push, times(3)).enviarNotificacionAUsuario(anyLong(), eq("IMAGENES_REDES"), anyString(), anyString(),
                any(), datos.capture());
        assertThat(datos.getValue()).containsEntry("pantalla", "IMAGENES_REDES").containsEntry("desde", "2026-11-13")
                .containsEntry("tipoImagen", "PREVIA");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinPartidosNoHayImagenesNiAviso() {

        when(dao.partidosEntre(VIERNES, DOMINGO)).thenReturn(List.of());

        assertThat(servicio.generarYAvisar("RESULTADOS", VIERNES)).isZero();

        verify(dao).borrar("RESULTADOS", VIERNES);
        verify(dao, never()).insertar(anyString(), anyString(), anyInt(), anyInt(), any(), any(), any(), any(), anyInt());
        verify(notificaciones, never()).crear(anyLong(), anyString(), anyString(), anyString(), any());
        verify(push, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), any(),
                anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void regenerarAManoNoAvisa() {

        servicio.regenerar(7L, "resultados", LocalDate.of(2026, 11, 14));

        verify(dao).borrar("RESULTADOS", VIERNES);
        verify(notificaciones, never()).crear(anyLong(), anyString(), anyString(), anyString(), any());
        verify(push, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), any(),
                anyMap());
    }

    @Test
    void soloCoordinacionPuedeListarDescargarYRegenerar() {

        assertThatThrownBy(() -> servicio.listar(5L, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.descargar(5L, 1L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.regenerar(5L, "PREVIA", null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.listar(null, null)).isInstanceOf(SecurityException.class);
        verify(dao, never()).borrar(anyString(), any());
    }

    @Test
    void tipoInvalidoYImagenInexistente() {

        assertThatThrownBy(() -> servicio.regenerar(7L, "OTRO", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.descargar(7L, 99L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listarDevuelveElFinDeSemanaYLosTextosSugeridos() {

        var respuesta = servicio.listar(7L, LocalDate.of(2026, 11, 11));

        assertThat(respuesta.desde()).isEqualTo(VIERNES);
        assertThat(respuesta.hasta()).isEqualTo(DOMINGO);
        assertThat(respuesta.textoPrevia()).endsWith("#somMTX #mareaAzul #MutxamelCF");
        assertThat(respuesta.textoResultados()).endsWith("#somMTX #mareaAzul #MutxamelCF");
    }

    @Test
    void contraUnFilialSeMarcaElRivalComoPropioYNoSePideEscudoDeRival() {

        when(dao.partidosEntre(VIERNES, DOMINGO)).thenReturn(List.of(
                new PartidoImagen(20L, 20L, "Juvenil A", "20", "F11", "Mutxamel C.F. B", 77L, VIERNES.plusDays(1),
                        "10:00", "Campo", true, false, null, null, null),
                new PartidoImagen(21L, 21L, "Juvenil B", "21", "F11", "Pego C.F.", 78L, VIERNES.plusDays(1), "12:00",
                        "Campo", true, false, null, null, null)));
        when(dao.escudoRival(78L)).thenReturn(new byte[] { 9 });

        servicio.generarYAvisar("PREVIA", VIERNES);

        ArgumentCaptor<com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Imagen> imagen = ArgumentCaptor
                .forClass(com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Imagen.class);
        verify(renderer).renderizar(imagen.capture());

        var filas = imagen.getValue().filas();

        assertThat(filas.get(0).rival()).isEqualTo("VS MUTXAMEL C.F. “B”");
        assertThat(filas.get(0).rivalPropio()).isTrue();
        assertThat(filas.get(0).escudo()).isNull();
        assertThat(filas.get(1).rivalPropio()).isFalse();
        assertThat(filas.get(1).escudo()).containsExactly(9);
        verify(dao, never()).escudoRival(77L);
    }
}
