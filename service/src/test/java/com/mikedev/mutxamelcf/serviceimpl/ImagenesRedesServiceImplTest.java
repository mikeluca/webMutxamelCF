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
import java.time.LocalDateTime;
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
import com.mikedev.mutxamelcf.model.ImagenRedes;
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

        // 7 es Retransmisión, 9 es administrador de la app; 5 (p. ej. un coordinador) no tiene ninguno de los dos roles.
        when(equipoGestionDao.esRetransmisionOAdmin(7L)).thenReturn(true);
        when(equipoGestionDao.esRetransmisionOAdmin(9L)).thenReturn(true);
        when(equipoGestionDao.obtenerRetransmision()).thenReturn(List.of(7L, 8L));
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of(9L));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(5L));
        when(notificaciones.puedeRecibir(anyLong(), anyString())).thenReturn(true);
        when(usuarioAppDao.obtenerIdioma(7L)).thenReturn("ca");
        when(usuarioAppDao.obtenerIdioma(8L)).thenReturn("en");
        when(usuarioAppDao.obtenerIdioma(9L)).thenReturn("es");
        when(renderer.renderizar(any())).thenReturn(new byte[] { 1 });
        when(dao.versionMaxima(anyString(), any())).thenReturn(2);
        when(dao.partidosEntre(VIERNES, DOMINGO)).thenReturn(partidos(9));
        when(dao.listar(VIERNES)).thenReturn(List.of());
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

    private static ImagenRedes generada(String tipo) {
        return new ImagenRedes(1L, tipo, "F8", 1, 1, VIERNES, DOMINGO, LocalDateTime.of(2026, 11, 13, 12, 0), 1);
    }

    // ---- permisos ------------------------------------------------------------------------------------

    @Test
    void retransmisionYAdminPuedenListarDescargarYRegenerar() {

        when(dao.obtenerPng(3L)).thenReturn(new byte[] { 5 });

        for (long usuario : new long[] { 7L, 9L }) {
            assertThat(servicio.listar(usuario, null)).isNotNull();
            assertThat(servicio.descargar(usuario, 3L)).containsExactly(5);
            assertThat(servicio.regenerar(usuario, "PREVIA", null)).isNotNull();
        }
    }

    @Test
    void coordinacionYElRestoNoPuedenListarDescargarNiRegenerar() {

        // El coordinador (5) ya no tiene acceso: 403 en las tres operaciones.
        assertThatThrownBy(() -> servicio.listar(5L, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.descargar(5L, 1L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.regenerar(5L, "PREVIA", null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.listar(null, null)).isInstanceOf(SecurityException.class);
        verify(dao, never()).borrar(anyString(), any());
        verify(equipoGestionDao, never()).esCoordinadorOAdmin(anyLong());
    }

    // ---- generación manual -----------------------------------------------------------------------------

    @Test
    void regenerarSustituyeLasImagenesDelFinDeSemanaYSubeLaVersion() {

        servicio.regenerar(7L, "previa", LocalDate.of(2026, 11, 14));

        // 9 partidos de F8 (el Primer Equipo está excluido) -> 2 páginas (5 + 4)
        verify(dao).borrar("PREVIA", VIERNES);
        verify(dao).insertar(eq("PREVIA"), eq("F8"), eq(1), eq(2), eq(VIERNES), eq(DOMINGO), any(), any(), eq(3));
        verify(dao).insertar(eq("PREVIA"), eq("F8"), eq(2), eq(2), eq(VIERNES), eq(DOMINGO), any(), any(), eq(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generarAManoNoAvisaANadie() {

        servicio.regenerar(7L, "resultados", LocalDate.of(2026, 11, 14));
        servicio.regenerar(9L, "previa", LocalDate.of(2026, 11, 14));

        verify(dao).borrar("RESULTADOS", VIERNES);
        verify(notificaciones, never()).crear(anyLong(), anyString(), anyString(), anyString(), any());
        verify(push, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), any(),
                anyMap());
    }

    @Test
    void tipoInvalidoYImagenInexistente() {

        assertThatThrownBy(() -> servicio.regenerar(7L, "OTRO", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.descargar(7L, 99L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.recordarSiPendiente("OTRO", VIERNES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listarDevuelveElFinDeSemanaYLosTextosSugeridos() {

        var respuesta = servicio.listar(7L, LocalDate.of(2026, 11, 11));

        assertThat(respuesta.desde()).isEqualTo(VIERNES);
        assertThat(respuesta.hasta()).isEqualTo(DOMINGO);
        assertThat(respuesta.textoPrevia()).endsWith("#somMTX #mareaAzul #MutxamelCF");
        assertThat(respuesta.textoResultados()).endsWith("#somMTX #mareaAzul #MutxamelCF");
    }

    // ---- recordatorio ------------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void sinImagenesGeneradasRecuerdaATodasLasCuentasDeRetransmisionEnSuIdioma() {

        int avisadas = servicio.recordarSiPendiente("PREVIA", VIERNES);

        assertThat(avisadas).isEqualTo(2);

        ArgumentCaptor<String> titulos = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textos = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> cuentas = ArgumentCaptor.forClass(Long.class);

        // La referencia es el viernes como yyyyMMdd
        verify(notificaciones, times(2)).crear(cuentas.capture(), eq("IMAGENES_REDES"), titulos.capture(),
                textos.capture(), eq(20261113L));
        assertThat(cuentas.getAllValues()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(titulos.getAllValues()).containsExactlyInAnyOrder("Imatges de la prèvia pendents",
                "Preview images pending");
        assertThat(textos.getAllValues()).anyMatch(t -> t.startsWith("Genera les imatges dels partits"));

        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(push, times(2)).enviarNotificacionAUsuario(anyLong(), eq("IMAGENES_REDES"), anyString(), anyString(),
                any(), datos.capture());
        assertThat(datos.getValue()).containsEntry("tipo", "IMAGENES_REDES").containsEntry("pantalla", "IMAGENES_REDES")
                .containsEntry("desde", "2026-11-13").containsEntry("tipoImagen", "PREVIA");

        // A coordinación y a los administradores no, mientras haya cuentas de Retransmisión.
        verify(notificaciones, never()).crear(eq(5L), anyString(), anyString(), anyString(), any());
        verify(notificaciones, never()).crear(eq(9L), anyString(), anyString(), anyString(), any());
    }

    @Test
    void elTextoDeResultadosEsElSuyoYSeAvisaConLaPestanaResultados() {

        servicio.recordarSiPendiente("RESULTADOS", VIERNES);

        ArgumentCaptor<String> titulos = ArgumentCaptor.forClass(String.class);
        verify(notificaciones, times(2)).crear(anyLong(), anyString(), titulos.capture(), anyString(), any());
        assertThat(titulos.getAllValues()).containsExactlyInAnyOrder("Imatges de resultats pendents",
                "Results images pending");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(push, times(2)).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), any(),
                datos.capture());
        assertThat(datos.getValue()).containsEntry("tipoImagen", "RESULTADOS");
    }

    @Test
    void elRecordatorioEnEspanolUsaElTextoDelEncargo() {

        when(equipoGestionDao.obtenerRetransmision()).thenReturn(List.of(9L));

        servicio.recordarSiPendiente("PREVIA", VIERNES);
        servicio.recordarSiPendiente("RESULTADOS", VIERNES);

        ArgumentCaptor<String> titulos = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textos = ArgumentCaptor.forClass(String.class);
        verify(notificaciones, times(2)).crear(eq(9L), anyString(), titulos.capture(), textos.capture(), any());

        assertThat(titulos.getAllValues()).containsExactly("Imágenes de la previa pendientes",
                "Imágenes de resultados pendientes");
        assertThat(textos.getAllValues()).containsExactly(
                "Genera las imágenes de los partidos de este fin de semana en Directo y RRSS › Imágenes para redes.",
                "Genera las imágenes de los resultados del fin de semana en Directo y RRSS › Imágenes para redes.");
    }

    @Test
    void siYaEstanGeneradasDeEseTipoNoSeAvisaANadie() {

        when(dao.listar(VIERNES)).thenReturn(List.of(generada("PREVIA")));

        assertThat(servicio.recordarSiPendiente("PREVIA", VIERNES)).isZero();

        verify(notificaciones, never()).crear(anyLong(), anyString(), anyString(), anyString(), any());
        verify(push, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), any(),
                anyMap());
    }

    @Test
    void estarGeneradaLaPreviaNoEvitaElRecordatorioDeResultados() {

        when(dao.listar(VIERNES)).thenReturn(List.of(generada("PREVIA")));

        assertThat(servicio.recordarSiPendiente("RESULTADOS", VIERNES)).isEqualTo(2);
    }

    @Test
    void sinNingunaCuentaDeRetransmisionSeAvisaALosAdministradoresDeLaApp() {

        when(equipoGestionDao.obtenerRetransmision()).thenReturn(List.of());

        assertThat(servicio.recordarSiPendiente("PREVIA", VIERNES)).isEqualTo(1);

        verify(notificaciones).crear(eq(9L), eq("IMAGENES_REDES"), anyString(), anyString(), eq(20261113L));
        verify(notificaciones, never()).crear(eq(5L), anyString(), anyString(), anyString(), any());
    }

    @Test
    void unaCuentaSinPermisoDeNotificacionesNoRecibeElRecordatorio() {

        when(notificaciones.puedeRecibir(8L, "COMUNICACION")).thenReturn(false);

        assertThat(servicio.recordarSiPendiente("PREVIA", VIERNES)).isEqualTo(1);

        verify(notificaciones, never()).crear(eq(8L), anyString(), anyString(), anyString(), any());
    }

    @Test
    void recordarNoGeneraNadaNiTocaLasImagenes() {

        servicio.recordarSiPendiente("PREVIA", VIERNES);

        verify(dao, never()).borrar(anyString(), any());
        verify(dao, never()).insertar(anyString(), anyString(), anyInt(), anyInt(), any(), any(), any(), any(),
                anyInt());
        verify(renderer, never()).renderizar(any());
    }

    // ---- rival propio ------------------------------------------------------------------------------------

    @Test
    void contraUnFilialSeMarcaElRivalComoPropioYNoSePideEscudoDeRival() {

        when(dao.partidosEntre(VIERNES, DOMINGO)).thenReturn(List.of(
                new PartidoImagen(20L, 20L, "Juvenil A", "20", "F11", "Mutxamel C.F. B", 77L, VIERNES.plusDays(1),
                        "10:00", "Campo", true, false, null, null, null),
                new PartidoImagen(21L, 21L, "Juvenil B", "21", "F11", "Pego C.F.", 78L, VIERNES.plusDays(1), "12:00",
                        "Campo", true, false, null, null, null)));
        when(dao.escudoRival(78L)).thenReturn(new byte[] { 9 });

        servicio.regenerar(7L, "PREVIA", VIERNES);

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
