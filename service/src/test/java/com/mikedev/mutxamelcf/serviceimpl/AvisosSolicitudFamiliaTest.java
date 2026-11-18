package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

/** Los avisos de las solicitudes de familia: idioma de cada cuenta, destinatarios y qué lleva (y qué no) cada texto. */
class AvisosSolicitudFamiliaTest {

    private NotificacionAppService notificaciones;
    private FcmPushService push;
    private UsuarioAppDao usuarioAppDao;
    private EquipoGestionDao equipoGestionDao;
    private com.mikedev.mutxamelcf.service.AvisosCuentaEmail emails;
    private AvisosSolicitudFamilia avisos;

    @BeforeEach
    void setUp() {
        notificaciones = mock(NotificacionAppService.class);
        push = mock(FcmPushService.class);
        usuarioAppDao = mock(UsuarioAppDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        emails = mock(com.mikedev.mutxamelcf.service.AvisosCuentaEmail.class);

        when(notificaciones.puedeRecibir(anyLong(), anyString())).thenReturn(true);
        when(usuarioAppDao.obtenerIdioma(anyLong())).thenReturn("es");
        when(usuarioAppDao.obtenerIdiomas(any())).thenReturn(Map.of());

        avisos = new AvisosSolicitudFamilia(notificaciones, push, new IdiomasUsuarios(usuarioAppDao),
                new TextosAvisos(), equipoGestionDao, emails);
    }

    @Test
    void avisaACoordinacionAdministradoresYEntrenadoresDeLosEquiposSinRepetirNiAvisarAlSolicitante() {

        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(1L, 9L));
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of(2L, 1L));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(8L)).thenReturn(List.of(3L, 2L));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(9L)).thenReturn(List.of(4L));

        Set<Long> destinos = avisos.destinatariosDeRevision(Set.of(8L, 9L), 9L);

        assertThat(destinos).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void elAvisoDeNuevaSolicitudNoLlevaDatosDelMenorNiDeLaFamiliaEnElPushNiEnLaBandeja() {

        avisos.avisarNuevaSolicitud(Set.of(1L), 12L);

        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bandeja = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).crear(eq(1L), eq("SOLICITUD_FAMILIA"), titulo.capture(), bandeja.capture(), eq(12L));

        ArgumentCaptor<String> pushTitulo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> pushTexto = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);
        verify(push).enviarNotificacionAUsuario(eq(1L), eq("SOLICITUD_FAMILIA"), pushTitulo.capture(),
                pushTexto.capture(), eq(12L), datos.capture());

        assertThat(pushTexto.getValue()).isEqualTo("Nueva solicitud de acceso de una familia");
        assertThat(pushTitulo.getValue()).isEqualTo("Nueva solicitud de acceso");

        // Textos fijos y genéricos: lo único variable es el id de la solicitud, y solo en el data.
        assertThat(bandeja.getValue()).isEqualTo(
                "Una familia ha pedido acceso a la información de su hijo o hija. Revísala en Solicitudes de familia.");
        for (String texto : List.of(titulo.getValue(), bandeja.getValue(), pushTitulo.getValue(), pushTexto.getValue())) {
            assertThat(texto).doesNotContain("12");
        }

        // La app abre la pantalla de solicitudes.
        assertThat(datos.getValue()).containsEntry("tipo", "SOLICITUD_FAMILIA")
                .containsEntry("pantalla", "SOLICITUDES_FAMILIA").containsEntry("solicitudId", "12");
    }

    @Test
    void cadaCuentaRecibeElAvisoEnSuIdioma() {

        when(usuarioAppDao.obtenerIdioma(1L)).thenReturn("ca");
        when(usuarioAppDao.obtenerIdioma(2L)).thenReturn("en");
        when(usuarioAppDao.obtenerIdioma(3L)).thenReturn("es");

        int avisados = avisos.avisarNuevaSolicitud(new java.util.LinkedHashSet<>(List.of(1L, 2L, 3L)), 1L);

        assertThat(avisados).isEqualTo(3);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(push, org.mockito.Mockito.times(3)).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(),
                texto.capture(), any(), any());

        assertThat(texto.getAllValues()).containsExactly("Nova sol·licitud d'accés d'una família",
                "New access request from a family", "Nueva solicitud de acceso de una familia");
    }

    @Test
    void unaCuentaQueNoQuiereAvisosNoRecibeNadaYUnFalloNoParaALasDemas() {

        when(notificaciones.puedeRecibir(1L, "COMUNICACION")).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("fcm caído")).when(push).enviarNotificacionAUsuario(eq(2L),
                anyString(), anyString(), anyString(), any(), any());

        int avisados = avisos.avisarNuevaSolicitud(new java.util.LinkedHashSet<>(List.of(1L, 2L, 3L)), 5L);

        assertThat(avisados).isEqualTo(1);
        verify(notificaciones, never()).crear(eq(1L), anyString(), anyString(), anyString(), any());
        verify(push).enviarNotificacionAUsuario(eq(3L), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void aprobadaDiceYaPuedesVerLaInformacionDelJugadorSoloAlFamiliar() {

        avisos.avisarAprobada(40L, "Luis Pérez Gómez", 12L);

        verify(notificaciones).crear(eq(40L), eq("SOLICITUD_FAMILIA_RESPUESTA"), eq("Acceso aprobado"),
                eq("Ya puedes ver la información de Luis Pérez Gómez."), eq(12L));
        verify(push).enviarNotificacionAUsuario(eq(40L), eq("SOLICITUD_FAMILIA_RESPUESTA"), eq("Acceso aprobado"),
                eq("Ya puedes ver la información de Luis Pérez Gómez."), eq(12L),
                eq(Map.of("tipo", "SOLICITUD_FAMILIA_RESPUESTA", "pantalla", "MI_SOLICITUD_FAMILIA", "solicitudId",
                        "12")));
        // Solo a esa cuenta: no se consulta a los familiares del jugador.
        verify(equipoGestionDao, never()).obtenerUsuariosFamiliaresPorJugador(anyLong());
        verify(equipoGestionDao, never()).obtenerUsuariosPorJugador(anyLong());
    }

    @Test
    void aprobadaTambienEnCatalanEIngles() {

        when(usuarioAppDao.obtenerIdioma(41L)).thenReturn("ca");
        when(usuarioAppDao.obtenerIdioma(42L)).thenReturn("en");

        avisos.avisarAprobada(41L, "Luis", 1L);
        avisos.avisarAprobada(42L, "Luis", 1L);

        verify(push).enviarNotificacionAUsuario(eq(41L), anyString(), eq("Accés aprovat"),
                eq("Ja pots veure la informació de Luis."), any(), any());
        verify(push).enviarNotificacionAUsuario(eq(42L), anyString(), eq("Access approved"),
                eq("You can now see the information of Luis."), any(), any());
    }

    @Test
    void rechazadaLlevaElMotivoSoloEnLaBandejaNuncaEnElPush() {

        avisos.avisarRechazada(40L, "Luis Pérez", "No es de nuestro club", 12L);

        ArgumentCaptor<String> bandeja = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).crear(eq(40L), eq("SOLICITUD_FAMILIA_RESPUESTA"), eq("Solicitud no aprobada"),
                bandeja.capture(), eq(12L));

        assertThat(bandeja.getValue()).startsWith("El club no ha podido aprobar el acceso a la información de Luis Pérez.")
                .endsWith("Motivo: No es de nuestro club");

        ArgumentCaptor<String> pushTexto = ArgumentCaptor.forClass(String.class);
        verify(push).enviarNotificacionAUsuario(eq(40L), anyString(), anyString(), pushTexto.capture(), eq(12L),
                any());

        assertThat(pushTexto.getValue()).isEqualTo("El club no ha podido aprobar el acceso a la información de Luis Pérez.")
                .doesNotContain("Motivo");
    }

    @Test
    void rechazadaSinMotivoNoAnadeElParrafo() {

        avisos.avisarRechazada(40L, "Luis Pérez", "  ", 12L);

        ArgumentCaptor<String> bandeja = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).crear(eq(40L), anyString(), anyString(), bandeja.capture(), any());

        assertThat(bandeja.getValue()).doesNotContain("Motivo");
    }

    @Test
    void elRecordatorioVaACoordinacionYAdministradoresSinDatosDeNadie() {

        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(1L));
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of(2L, 1L));

        int avisados = avisos.recordarACoordinacion();

        assertThat(avisados).isEqualTo(2);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(push, org.mockito.Mockito.times(2)).enviarNotificacionAUsuario(anyLong(), eq("SOLICITUD_FAMILIA"),
                eq("Solicitudes de familia pendientes"), texto.capture(), eq(null), any());

        assertThat(texto.getAllValues()).containsOnly(
                "Hay solicitudes de acceso de familias sin revisar desde hace más de 2 días.");
        verify(equipoGestionDao, never()).obtenerEntrenadoresPorEquipo(anyLong());
    }

    @Test
    void elEmailDeAprobacionVaEnElIdiomaDeLaCuentaYElPrimerHijoLlevaElCompleto() {

        when(usuarioAppDao.obtenerIdioma(40L)).thenReturn("ca");

        avisos.emailAprobada(40L, "ana@example.com", "Ana", "Pepe García", true);
        avisos.emailAprobada(40L, "ana@example.com", "Ana", "Ana García", false);

        verify(emails).enviarSolicitudFamiliaAprobada("ana@example.com", "Ana", List.of("Pepe García"), "ca");
        verify(emails).enviarSolicitudFamiliaOtroAprobado("ana@example.com", "Ana", "Ana García", "ca");
        verify(emails, never()).enviarSolicitudFamiliaRechazada(any(), any(), any(), any(), any());
    }

    @Test
    void elEmailDeRechazoLlevaElMotivoYElIdioma() {

        when(usuarioAppDao.obtenerIdioma(40L)).thenReturn("en");

        avisos.emailRechazada(40L, "ana@example.com", "Ana", "Luis Pérez", "No es de nuestro club");

        verify(emails).enviarSolicitudFamiliaRechazada("ana@example.com", "Ana", "Luis Pérez",
                "No es de nuestro club", "en");
    }

    @Test
    void siElEmailFallaONoSaleLaAprobacionNoSeRompe() {

        when(emails.enviarSolicitudFamiliaAprobada(any(), any(), any(), any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp")).when(emails).enviarSolicitudFamiliaRechazada(any(),
                any(), any(), any(), any());

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            avisos.emailAprobada(40L, "a@b.es", "Ana", "Luis", true);
            avisos.emailRechazada(40L, "a@b.es", "Ana", "Luis", null);
        }).doesNotThrowAnyException();
    }
}
