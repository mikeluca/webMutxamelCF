package com.mikedev.mutxamelcf.mvc.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.mvc.communication.InvitacionUsuario;
import com.mikedev.mutxamelcf.mvc.communication.ReenvioActivacion;
import com.mikedev.mutxamelcf.mvc.controller.ImportacionClasificacionesAdminController;
import com.mikedev.mutxamelcf.mvc.controller.ImportacionFfcvAdminController;
import com.mikedev.mutxamelcf.service.ImportacionClasificacionesService;
import com.mikedev.mutxamelcf.service.ImportacionFfcvService;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.serviceimpl.LimitadorReenvioActivacion;
import com.mikedev.mutxamelcf.serviceimpl.UsuarioAppServiceImpl;

/**
 * Las rutas de administración desde la app para ADMIN_APP: importar los datos de la FFCV y gestionar los usuarios.
 * Con el UsuarioAppServiceImpl real sobre DAOs simulados, así que los permisos por rol son los de verdad.
 */
class AppAdminFfcvUsuariosControllerTest {

    private static final int ADMIN = 7;
    private static final int OTRO_ADMIN = 8;
    private static final int FAMILIA = 30;
    private static final int PENDIENTE = 40;

    private UsuarioAppDao usuarioAppDao;
    private UsuarioAppVinculoDao vinculoDao;
    private RolAppDao rolAppDao;
    private ComunicacionesService comunicaciones;
    private CuentaActivadaNotifier notifier;
    private ImportacionFfcvService ffcv;
    private ImportacionClasificacionesService clasificaciones;
    private MutableClock reloj;
    private MockMvc mvc;

    /** Reloj que se puede adelantar (para la vigencia de la vista previa). */
    private static final class MutableClock extends Clock {
        private Instant ahora = Instant.parse("2026-11-23T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }
    }

    @BeforeEach
    void setUp() {
        usuarioAppDao = mock(UsuarioAppDao.class);
        vinculoDao = mock(UsuarioAppVinculoDao.class);
        rolAppDao = mock(RolAppDao.class);
        comunicaciones = mock(ComunicacionesService.class);
        notifier = mock(CuentaActivadaNotifier.class);
        ffcv = mock(ImportacionFfcvService.class);
        clasificaciones = mock(ImportacionClasificacionesService.class);
        reloj = new MutableClock();

        UsuarioAppServiceImpl usuarios = new UsuarioAppServiceImpl(usuarioAppDao, vinculoDao, rolAppDao,
                mock(PasswordEncoder.class), mock(JwtService.class), reloj);

        AppAdminUsuariosController usuariosController = new AppAdminUsuariosController(usuarios,
                new InvitacionUsuario(usuarios, comunicaciones),
                new ReenvioActivacion(usuarios, comunicaciones, new LimitadorReenvioActivacion(reloj)), notifier);
        AppAdminFfcvController ffcvController = new AppAdminFfcvController(ffcv, clasificaciones, usuarios,
                new PrevisualizacionesClasificacionesApp(reloj));

        mvc = MockMvcBuilders.standaloneSetup(usuariosController, ffcvController)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()))
                .build();

        roles(ADMIN, "ADMIN_APP");
        roles(OTRO_ADMIN, "ADMIN_APP");
        roles(FAMILIA, "FAMILIAR");
        roles(50, "COORDINADOR");
        roles(51, "ENTRENADOR");
        roles(52, "RRSS");
        roles(53, "RETRANSMISION");

        cuenta(FAMILIA, "familia@example.com", "Ana", true, false);
        cuenta(PENDIENTE, "pendiente@example.com", "Luis", false, true);
        cuenta(OTRO_ADMIN, "otro@example.com", "Otro", true, false);
        cuenta(ADMIN, "admin@example.com", "Admin", true, false);
        when(vinculoDao.obtenerVinculos(anyInt())).thenReturn(List.of());
        UsuarioApp familia = usuarioAppDao.obtenerPorId(FAMILIA);
        UsuarioApp pendiente = usuarioAppDao.obtenerPorId(PENDIENTE);
        when(usuarioAppDao.listarTodos()).thenReturn(List.of(familia, pendiente));
        when(comunicaciones.enviarInvitacionApp(anyString(), any(), anyString())).thenReturn(true);
    }

    private void roles(int usuario, String... codigos) {
        int id = 1;
        List<RolApp> lista = new java.util.ArrayList<>();
        for (String c : codigos) {
            RolApp r = new RolApp();
            r.setId(id++);
            r.setCodigo(c);
            r.setNombre(c);
            lista.add(r);
        }
        when(rolAppDao.obtenerPorUsuario(usuario)).thenReturn(lista);
    }

    private void cuenta(int id, String email, String nombre, boolean activo, boolean conToken) {
        UsuarioApp u = new UsuarioApp();
        u.setId(id);
        u.setEmail(email);
        u.setNombre(nombre);
        u.setActivo(activo);
        if (conToken) {
            u.setTokenActivacion("123456");
        }
        when(usuarioAppDao.obtenerPorId(id)).thenReturn(u);
    }

    private static RequestPostProcessor como(int usuario) {
        Authentication auth = new UsernamePasswordAuthenticationToken(String.valueOf(usuario), null, List.of());
        return request -> {
            request.setUserPrincipal(auth);
            return request;
        };
    }

    // ---- permisos ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = { 50, 51, 52, 53, FAMILIA })
    void soloAdminAppEntraEnCadaRutaDeUsuariosYFfcvYElRestoRecibe403(int usuario) throws Exception {

        MockMultipartFile csv = new MockMultipartFile("archivo", "LstPartidos.csv", "text/csv", "x".getBytes());
        MockMultipartFile xlsx = new MockMultipartFile("archivo", "ListadoClasificaciones.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes());
        String json = "{}";

        mvc.perform(get("/api/app/admin/usuarios").with(como(usuario))).andExpect(status().isForbidden());
        mvc.perform(get("/api/app/admin/usuarios/opciones").with(como(usuario))).andExpect(status().isForbidden());

        for (String ruta : List.of("invitar", FAMILIA + "/nombre", FAMILIA + "/vinculos", FAMILIA + "/vinculos/quitar",
                FAMILIA + "/reenviar", FAMILIA + "/activar", FAMILIA + "/desactivar", FAMILIA + "/eliminar")) {
            mvc.perform(post("/api/app/admin/usuarios/" + ruta).with(como(usuario))
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        }

        mvc.perform(multipart("/api/app/admin/ffcv/partidos/previsualizar").file(csv).with(como(usuario)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/app/admin/ffcv/partidos/confirmar").with(como(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsualizacion\":{}}"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar").file(xlsx).with(como(usuario)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/app/admin/ffcv/clasificaciones/confirmar").with(como(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());

        verify(ffcv, never()).previsualizar(any());
        verify(clasificaciones, never()).previsualizar(any());
        verify(usuarioAppDao, never()).desactivarUsuario(anyInt());
    }

    @Test
    void sinSesionEs401() throws Exception {
        mvc.perform(get("/api/app/admin/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/app/admin/ffcv/partidos/previsualizar")
                .file(new MockMultipartFile("archivo", "a.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    // ---- FFCV: partidos ----------------------------------------------------------------------------------------------

    private static final PrevisualizacionFfcv PREVIA_PARTIDOS = new PrevisualizacionFfcv(null, null, List.of(),
            List.of(), List.of(), List.of());

    @Test
    void partidosPrevisualizaYConfirmaComoElPanel() throws Exception {

        byte[] contenido = "datos".getBytes();
        when(ffcv.previsualizar(contenido)).thenReturn(PREVIA_PARTIDOS);
        when(ffcv.confirmar(eq(String.valueOf(ADMIN)), any(), any()))
                .thenReturn(new ResultadoImportacionFfcv(3, 2, 1, 0, 0, 1, 0, List.of("aviso")));

        mvc.perform(multipart("/api/app/admin/ffcv/partidos/previsualizar")
                .file(new MockMultipartFile("archivo", "LstPartidos.csv", "text/csv", contenido)).with(como(ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas").isArray());

        mvc.perform(post("/api/app/admin/ffcv/partidos/confirmar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"previsualizacion\":{\"filas\":[],\"descansan\":[],\"desaparecidos\":[],\"errores\":[]},"
                        + "\"asignaciones\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.creados").value(3))
                .andExpect(jsonPath("$.avisos[0]").value("aviso"));
    }

    @Test
    void partidosConUnFicheroNoValidoDa400ConLosMensajesDelPanel() throws Exception {

        mvc.perform(multipart("/api/app/admin/ffcv/partidos/previsualizar")
                .file(new MockMultipartFile("archivo", "LstPartidos.csv", "text/csv", new byte[0])).with(como(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Selecciona el archivo LstPartidos.csv"));

        byte[] enorme = new byte[(int) ImportacionFfcvAdminController.TAMANO_MAXIMO_BYTES + 1];
        mvc.perform(multipart("/api/app/admin/ffcv/partidos/previsualizar")
                .file(new MockMultipartFile("archivo", "LstPartidos.csv", "text/csv", enorme)).with(como(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo es demasiado grande para ser un listado de partidos"));

        when(ffcv.previsualizar(any())).thenThrow(new IllegalArgumentException("El archivo no es un LstPartidos.csv"));
        mvc.perform(multipart("/api/app/admin/ffcv/partidos/previsualizar")
                .file(new MockMultipartFile("archivo", "otro.csv", "text/csv", "basura".getBytes())).with(como(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo no es un LstPartidos.csv"));

        mvc.perform(post("/api/app/admin/ffcv/partidos/confirmar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No hay nada que confirmar"));
    }

    // ---- FFCV: clasificaciones ---------------------------------------------------------------------------------------

    private static final PrevisualizacionClasificaciones PREVIA_CLASIF = new PrevisualizacionClasificaciones(10, 2,
            List.of(), List.of(), List.of());

    private MockMultipartFile xlsx(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, MediaType.APPLICATION_OCTET_STREAM_VALUE, contenido);
    }

    @Test
    void clasificacionesPrevisualizaYConfirmaLaVistaPreviaGuardadaUnaSolaVez() throws Exception {

        byte[] contenido = "xlsx".getBytes();
        when(clasificaciones.previsualizar(contenido)).thenReturn(PREVIA_CLASIF);
        when(clasificaciones.confirmar(eq(String.valueOf(ADMIN)), eq(PREVIA_CLASIF), any()))
                .thenReturn(new ResultadoImportacionClasificaciones(2, 20, List.of("Alevín A"), List.of()));

        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar")
                .file(xlsx("ListadoClasificaciones.xlsx", contenido)).with(como(ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filasLeidas").value(10));

        mvc.perform(post("/api/app/admin/ffcv/clasificaciones/confirmar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"equipoIds\":[1,2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importados").value(2))
                .andExpect(jsonPath("$.equipos[0]").value("Alevín A"));

        verify(clasificaciones).confirmar(String.valueOf(ADMIN), PREVIA_CLASIF, List.of(1L, 2L));

        // Importada: no se puede confirmar dos veces la misma vista previa.
        mvc.perform(post("/api/app/admin/ffcv/clasificaciones/confirmar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"equipoIds\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La vista previa ha caducado: vuelve a subir el archivo"));
    }

    @Test
    void clasificacionesSinVistaPreviaOCaducadaDa400() throws Exception {

        when(clasificaciones.previsualizar(any())).thenReturn(PREVIA_CLASIF);

        mvc.perform(post("/api/app/admin/ffcv/clasificaciones/confirmar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"equipoIds\":[1]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar")
                .file(xlsx("ListadoClasificaciones.xlsx", "x".getBytes())).with(como(ADMIN))).andExpect(status().isOk());
        reloj.avanzar(Duration.ofMinutes(31));

        mvc.perform(post("/api/app/admin/ffcv/clasificaciones/confirmar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"equipoIds\":[1]}"))
                .andExpect(status().isBadRequest());
        verify(clasificaciones, never()).confirmar(anyString(), any(), any());
    }

    @Test
    void clasificacionesConUnFicheroNoValidoDa400ConLosMensajesDelPanel() throws Exception {

        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar").file(xlsx("a.xlsx", new byte[0]))
                .with(como(ADMIN))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Selecciona el archivo ListadoClasificaciones.xlsx"));

        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar")
                .file(xlsx("clasificaciones.csv", "x".getBytes())).with(como(ADMIN))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo debe ser un Excel .xlsx (ListadoClasificaciones.xlsx)"));

        byte[] enorme = new byte[(int) ImportacionClasificacionesAdminController.TAMANO_MAXIMO_BYTES + 1];
        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar").file(xlsx("a.xlsx", enorme))
                .with(como(ADMIN))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error")
                        .value("El archivo es demasiado grande para ser un listado de clasificaciones"));

        when(clasificaciones.previsualizar(any())).thenThrow(new IllegalArgumentException("No es un listado válido"));
        mvc.perform(multipart("/api/app/admin/ffcv/clasificaciones/previsualizar")
                .file(xlsx("ListadoClasificaciones.xlsx", "basura".getBytes())).with(como(ADMIN)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("No es un listado válido"));
    }

    // ---- usuarios ---------------------------------------------------------------------------------------------------

    @Test
    void elListadoAceptaLosFiltrosPorQuery() throws Exception {

        when(rolAppDao.obtenerTodos()).thenReturn(List.of());
        roles(FAMILIA, "RRSS");
        roles(PENDIENTE, "JUGADOR");

        mvc.perform(get("/api/app/admin/usuarios").with(como(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/app/admin/usuarios").param("q", "ANA").with(como(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(FAMILIA))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA")).andExpect(jsonPath("$[0].roles[0].codigo")
                        .value("RRSS"));
        mvc.perform(get("/api/app/admin/usuarios").param("rol", "JUGADOR").with(como(ADMIN)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(PENDIENTE));
        mvc.perform(get("/api/app/admin/usuarios").param("estado", "PENDIENTE").with(como(ADMIN)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(PENDIENTE));
        mvc.perform(get("/api/app/admin/usuarios").param("rol", "SIN_ROLES").with(como(ADMIN)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/app/admin/usuarios").param("estado", "RARA").with(como(ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invitarCreaLaCuentaYManda201ConElEmailYUnEmailRepetidoDa409() throws Exception {

        RolApp rrss = new RolApp();
        rrss.setId(6);
        rrss.setCodigo("RRSS");
        when(rolAppDao.obtenerPorCodigo("RRSS")).thenReturn(rrss);
        when(usuarioAppDao.guardar(any())).thenReturn(60);
        cuenta(60, "nueva@example.com", "Ana", false, true);
        when(rolAppDao.obtenerPorUsuario(60)).thenReturn(List.of());

        String cuerpo = "{\"email\":\"nueva@example.com\",\"nombre\":\"Ana\",\"roles\":[\"RRSS\"]}";

        mvc.perform(post("/api/app/admin/usuarios/invitar").with(como(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("nueva@example.com"))
                .andExpect(jsonPath("$.emailEnviado").value(true));
        verify(rolAppDao).asignarRol(60, 6);

        UsuarioApp nueva = usuarioAppDao.obtenerPorId(60);
        when(usuarioAppDao.obtenerPorEmail("nueva@example.com")).thenReturn(nueva);
        mvc.perform(post("/api/app/admin/usuarios/invitar").with(como(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andExpect(status().isConflict());

        // ADMIN_APP no se asigna.
        mvc.perform(post("/api/app/admin/usuarios/invitar").with(como(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.com\",\"nombre\":\"X\",\"roles\":[\"ADMIN_APP\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editaElNombreYAnadeYQuitaVinculos() throws Exception {

        cuenta(61, "sinficha@example.com", null, true, false);

        mvc.perform(post("/api/app/admin/usuarios/61/nombre").with(como(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Ana\",\"apellidos\":\"Gil\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Nombre actualizado correctamente."));
        verify(usuarioAppDao).actualizarNombre(61, "Ana", "Gil");

        RolApp rrss = new RolApp();
        rrss.setId(6);
        rrss.setCodigo("RRSS");
        when(rolAppDao.obtenerPorCodigo("RRSS")).thenReturn(rrss);
        when(rolAppDao.obtenerPorUsuario(61)).thenReturn(List.of());

        mvc.perform(post("/api/app/admin/usuarios/61/vinculos").with(como(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vinculos\":[{\"tipo\":\"RRSS\"}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Vínculo/rol añadido correctamente."));
        verify(rolAppDao).asignarRol(61, 6);

        mvc.perform(post("/api/app/admin/usuarios/61/vinculos/quitar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"RRSS\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Vínculo/rol quitado correctamente."));
        verify(rolAppDao).eliminarRol(61, 6);

        mvc.perform(post("/api/app/admin/usuarios/61/vinculos").with(como(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vinculos\":[{\"tipo\":\"ADMIN_APP\"}]}")).andExpect(status().isBadRequest());
    }

    @Test
    void activaYDesactivaYLasReglasDeSeguridadDan400() throws Exception {

        mvc.perform(post("/api/app/admin/usuarios/" + FAMILIA + "/desactivar").with(como(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cuenta desactivada."));
        verify(usuarioAppDao).desactivarUsuario(FAMILIA);

        mvc.perform(post("/api/app/admin/usuarios/" + FAMILIA + "/activar").with(como(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cuenta activada."));
        verify(usuarioAppDao).activarUsuario(FAMILIA);
        verify(notifier).notificar(FAMILIA);

        // No puede desactivarse ni tocar a otro administrador.
        mvc.perform(post("/api/app/admin/usuarios/" + ADMIN + "/desactivar").with(como(ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/app/admin/usuarios/" + OTRO_ADMIN + "/desactivar").with(como(ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/app/admin/usuarios/" + OTRO_ADMIN + "/vinculos/quitar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"COORDINADOR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/app/admin/usuarios/" + ADMIN + "/vinculos/quitar").with(como(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"ADMIN_APP\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioAppDao, never()).desactivarUsuario(ADMIN);
        verify(usuarioAppDao, never()).desactivarUsuario(OTRO_ADMIN);
    }

    @Test
    void eliminaUnaInvitacionSinActivarPeroNoUnaCuentaActiva() throws Exception {

        mvc.perform(post("/api/app/admin/usuarios/" + PENDIENTE + "/eliminar").with(como(ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Invitación eliminada."));
        verify(usuarioAppDao).eliminar(PENDIENTE);

        mvc.perform(post("/api/app/admin/usuarios/" + FAMILIA + "/eliminar").with(como(ADMIN)))
                .andExpect(status().isBadRequest());
        verify(usuarioAppDao, never()).eliminar(FAMILIA);
    }

    @Test
    void reenviarMandaElEmailYElSegundoReenvioSeguidoDa429() throws Exception {

        mvc.perform(post("/api/app/admin/usuarios/" + PENDIENTE + "/reenviar").with(como(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Invitación reenviada a pendiente@example.com."));
        verify(comunicaciones).enviarInvitacionApp(eq("pendiente@example.com"), any(), anyString());

        mvc.perform(post("/api/app/admin/usuarios/" + PENDIENTE + "/reenviar").with(como(ADMIN)))
                .andExpect(status().isTooManyRequests());

        // Pasados los 10 minutos vuelve a dejar.
        reloj.avanzar(Duration.ofMinutes(11));
        mvc.perform(post("/api/app/admin/usuarios/" + PENDIENTE + "/reenviar").with(como(ADMIN)))
                .andExpect(status().isOk());

        // Una cuenta activa no se reenvía.
        mvc.perform(post("/api/app/admin/usuarios/" + FAMILIA + "/reenviar").with(como(ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lasOpcionesTraenLosRolesAsignablesSinAdminApp() throws Exception {

        for (String codigo : List.of("COORDINADOR", "RETRANSMISION", "RRSS", "ADMIN_APP")) {
            RolApp r = new RolApp();
            r.setCodigo(codigo);
            r.setNombre(codigo);
            when(rolAppDao.obtenerPorCodigo(codigo)).thenReturn(r);
        }

        mvc.perform(get("/api/app/admin/usuarios/opciones").with(como(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(3)).andExpect(jsonPath("$.roles[2].codigo").value("RRSS"))
                .andExpect(jsonPath("$.personas.jugadores").isArray());
    }

}
