package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.JugadorCandidato;
import com.mikedev.mutxamelcf.model.SolicitudFamilia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaJugador;

/** Las solicitudes de familia (SolicitudFamiliaDaoImpl) y la búsqueda de fichas de familiar sobre H2 en modo Oracle. */
class SolicitudFamiliaDaoH2Test {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 18, 10, 0);

    private JdbcTemplate jdbc;
    private SolicitudFamiliaDaoImpl dao;
    private FamiliarDaoImpl familiarDao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100))");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearSolicitudesFamilia(jdbc);

        jdbc.execute("CREATE SEQUENCE FAMILIARES_SEQ");
        jdbc.execute("""
                CREATE TABLE FAMILIARES (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), APELLIDOS VARCHAR2(100),
                    TELEFONO VARCHAR2(30), EMAIL VARCHAR2(150), RECIBE_INFO_CLUB NUMBER, WHATSAPP_ACTIVO NUMBER)""");

        dao = new SolicitudFamiliaDaoImpl(jdbc);
        familiarDao = new FamiliarDaoImpl(jdbc);
    }

    private static SolicitudFamilia solicitud(long usuario, String estado, LocalDateTime fecha) {
        return new SolicitudFamilia(null, usuario, "Ana", "Pérez Gil", "ana" + usuario + "@example.com", "623176818",
                "MADRE", estado, null, fecha, null, null, null);
    }

    private static SolicitudFamiliaJugador hijo(String nombre, String apellidos, String equipo) {
        return new SolicitudFamiliaJugador(null, null, nombre, apellidos, equipo, null, "PENDIENTE");
    }

    @Test
    void insertaLaSolicitudConSusHijosPendientes() {

        long id = dao.insertar(solicitud(1, "PENDIENTE_EMAIL", AHORA),
                List.of(hijo("Luis", "Pérez", "Alevín B"), hijo("Eva", "Pérez", null)));

        SolicitudFamilia leida = dao.obtenerPorId(id);

        assertThat(leida.usuarioAppId()).isEqualTo(1);
        assertThat(leida.estado()).isEqualTo("PENDIENTE_EMAIL");
        assertThat(leida.parentesco()).isEqualTo("MADRE");
        assertThat(leida.fechaSolicitud()).isEqualTo(AHORA);
        assertThat(leida.fechaRevision()).isNull();

        List<SolicitudFamiliaJugador> hijos = dao.obtenerHijos(id);

        assertThat(hijos).extracting(SolicitudFamiliaJugador::nombre).containsExactly("Luis", "Eva");
        assertThat(hijos).extracting(SolicitudFamiliaJugador::estado).containsOnly("PENDIENTE");
        assertThat(hijos.get(1).equipoTexto()).isNull();
        assertThat(hijos.get(0).jugadorId()).isNull();
    }

    @Test
    void lasDosCasillasSonObligatoriasEnLaBaseDeDatos() {

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO SOLICITUDES_FAMILIA (USUARIO_APP_ID, NOMBRE, APELLIDOS, EMAIL, PARENTESCO,
                    ACEPTA_PRIVACIDAD, DECLARA_REPRESENTANTE)
                VALUES (1, 'A', 'B', 'a@b.es', 'MADRE', 1, 0)""")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void confirmarElEmailSoloCambiaLasPendientesDeEmail() {

        long id = dao.insertar(solicitud(1, "PENDIENTE_EMAIL", AHORA), List.of(hijo("Luis", "Pérez", null)));

        assertThat(dao.confirmarEmail(id)).isEqualTo(1);
        assertThat(dao.obtenerPorId(id).estado()).isEqualTo("PENDIENTE");
        assertThat(dao.confirmarEmail(id)).isZero();
    }

    @Test
    void laSolicitudAbiertaDeUnaCuentaEsLaPendienteMasReciente() {

        long cerrada = dao.insertar(solicitud(1, "PENDIENTE", AHORA.minusDays(3)), List.of(hijo("A", "B", null)));
        dao.cerrar(cerrada, "APROBADA", null, AHORA, 9L, null);
        long abierta = dao.insertar(solicitud(1, "PENDIENTE", AHORA), List.of(hijo("C", "D", null)));
        dao.insertar(solicitud(2, "PENDIENTE", AHORA), List.of(hijo("E", "F", null)));

        assertThat(dao.obtenerAbiertaDeUsuario(1).id()).isEqualTo(abierta);
        assertThat(dao.obtenerAbiertaDeUsuario(3)).isNull();
        assertThat(dao.obtenerPorUsuario(1)).extracting(SolicitudFamilia::id).containsExactly(abierta, cerrada);
    }

    @Test
    void aprobarYRechazarHijosYCerrarLaSolicitud() {

        long id = dao.insertar(solicitud(1, "PENDIENTE", AHORA), List.of(hijo("Luis", "Pérez", null), hijo("Eva", "P", null)));
        List<SolicitudFamiliaJugador> hijos = dao.obtenerHijos(id);

        dao.aprobarHijo(hijos.get(0).id(), 55L);
        dao.rechazarHijo(hijos.get(1).id());
        dao.actualizarMotivo(id, "No es de nuestro club");
        dao.cerrar(id, "APROBADA", "No es de nuestro club", AHORA.plusHours(1), 7L, null);

        List<SolicitudFamiliaJugador> despues = dao.obtenerHijos(id);

        assertThat(despues.get(0).estado()).isEqualTo("APROBADO");
        assertThat(despues.get(0).jugadorId()).isEqualTo(55L);
        assertThat(despues.get(1).estado()).isEqualTo("RECHAZADO");
        assertThat(despues.get(1).jugadorId()).isNull();

        SolicitudFamilia cerrada = dao.obtenerPorId(id);

        assertThat(cerrada.estado()).isEqualTo("APROBADA");
        assertThat(cerrada.motivoRechazo()).isEqualTo("No es de nuestro club");
        assertThat(cerrada.revisadaPorId()).isEqualTo(7L);
        assertThat(cerrada.revisadaPorPanel()).isNull();
        assertThat(cerrada.fechaRevision()).isEqualTo(AHORA.plusHours(1));
    }

    @Test
    void unHijoAprobadoExigeJugador() {

        long id = dao.insertar(solicitud(1, "PENDIENTE", AHORA), List.of(hijo("Luis", "Pérez", null)));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE SOLICITUDES_FAMILIA_JUGADOR SET ESTADO = 'APROBADO' WHERE SOLICITUD_ID = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laRevisionDesdeElPanelGuardaElUsuarioDelPanel() {

        long id = dao.insertar(solicitud(1, "PENDIENTE", AHORA), List.of(hijo("Luis", "Pérez", null)));

        dao.cerrar(id, "RECHAZADA", null, AHORA, null, "admin");

        assertThat(dao.obtenerPorId(id).revisadaPorPanel()).isEqualTo("admin");
        assertThat(dao.obtenerPorId(id).revisadaPorId()).isNull();
    }

    @Test
    void listaLasCaducablesYLasPendientesAntiguas() {

        LocalDateTime hace8 = AHORA.minusDays(8);
        LocalDateTime hace3 = AHORA.minusDays(3);

        long sinEmail = dao.insertar(solicitud(1, "PENDIENTE_EMAIL", hace8), List.of(hijo("A", "B", null)));
        long pendienteVieja = dao.insertar(solicitud(2, "PENDIENTE", hace8), List.of(hijo("A", "B", null)));
        long pendienteNueva = dao.insertar(solicitud(3, "PENDIENTE", hace3), List.of(hijo("A", "B", null)));
        long rechazadaVieja = dao.insertar(solicitud(4, "PENDIENTE", hace8), List.of(hijo("A", "B", null)));
        dao.cerrar(rechazadaVieja, "RECHAZADA", null, hace8, 9L, null);
        long rechazadaReciente = dao.insertar(solicitud(5, "PENDIENTE", hace8), List.of(hijo("A", "B", null)));
        dao.cerrar(rechazadaReciente, "RECHAZADA", null, hace3, 9L, null);
        long aprobadaVieja = dao.insertar(solicitud(6, "PENDIENTE", hace8), List.of(hijo("A", "B", null)));
        dao.cerrar(aprobadaVieja, "APROBADA", null, hace8, 9L, null);

        assertThat(dao.listarCaducables(AHORA.minusDays(7))).extracting(SolicitudFamilia::id)
                .containsExactlyInAnyOrder(sinEmail, pendienteVieja, rechazadaVieja);

        assertThat(dao.listarPendientesAnterioresA(AHORA.minusDays(2))).extracting(SolicitudFamilia::id)
                .containsExactlyInAnyOrder(pendienteVieja, pendienteNueva);

        assertThat(dao.contarPorEstado("PENDIENTE")).isEqualTo(2);
        assertThat(dao.listarRevisadasDesde(AHORA.minusDays(7))).extracting(SolicitudFamilia::id)
                .containsExactly(rechazadaReciente);
    }

    @Test
    void caducarBorraLaSolicitudYSusHijos() {

        long id = dao.insertar(solicitud(1, "PENDIENTE", AHORA.minusDays(9)), List.of(hijo("Luis", "Pérez", "Alevín")));

        dao.caducar(id);

        assertThat(dao.obtenerPorId(id)).isNull();
        assertThat(dao.obtenerHijos(id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SOLICITUDES_FAMILIA_JUGADOR", Integer.class)).isZero();
    }

    @Test
    void repetirLaSolicitudAntesDeConfirmarSustituyeLosDatosYLosHijos() {

        long id = dao.insertar(solicitud(1, "PENDIENTE_EMAIL", AHORA.minusHours(2)), List.of(hijo("Luis", "Pérez", null)));

        SolicitudFamilia nueva = new SolicitudFamilia(null, 1L, "Anita", "Gil", "ana1@example.com", null, "TUTOR",
                "PENDIENTE_EMAIL", null, AHORA, null, null, null);

        dao.reemplazarDatos(id, nueva, List.of(hijo("Eva", "Gil", "Cadete"), hijo("Pau", "Gil", null)));

        SolicitudFamilia leida = dao.obtenerPorId(id);

        assertThat(leida.nombre()).isEqualTo("Anita");
        assertThat(leida.parentesco()).isEqualTo("TUTOR");
        assertThat(leida.telefono()).isNull();
        assertThat(leida.fechaSolicitud()).isEqualTo(AHORA);
        assertThat(dao.obtenerHijos(id)).extracting(SolicitudFamiliaJugador::nombre).containsExactly("Eva", "Pau");
    }

    @Test
    void losJugadoresActivosSalenSinBajasYConLaCategoriaDelEquipo() {

        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA) VALUES (1, 'Alevín B', 'Alevín')");
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, EQUIPO_ID, CATEGORIA) VALUES (10, 'Luis', 'Pérez Gómez', 1, 'Otra')");
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, CATEGORIA) VALUES (11, 'Eva', 'Ruiz', 'Prueba')");
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, EQUIPO_ID, FECHA_BAJA) VALUES (12, 'Baja', 'Baja', 1, DATE '2026-01-01')");

        List<JugadorCandidato> activos = dao.listarJugadoresActivos();

        assertThat(activos).extracting(JugadorCandidato::id).containsExactlyInAnyOrder(10L, 11L);

        JugadorCandidato luis = activos.stream().filter(j -> j.id() == 10).findFirst().orElseThrow();

        assertThat(luis.equipoId()).isEqualTo(1L);
        assertThat(luis.equipo()).isEqualTo("Alevín B");
        assertThat(luis.categoria()).isEqualTo("Alevín");

        JugadorCandidato eva = activos.stream().filter(j -> j.id() == 11).findFirst().orElseThrow();

        assertThat(eva.equipoId()).isNull();
        assertThat(eva.categoria()).isEqualTo("Prueba");
    }

    // ---- la ficha de familiar por email o teléfono --------------------------------------------------------------------

    private void familiar(long id, String email, String telefono) {
        jdbc.update("INSERT INTO FAMILIARES (ID, NOMBRE, APELLIDOS, TELEFONO, EMAIL, RECIBE_INFO_CLUB, WHATSAPP_ACTIVO)"
                + " VALUES (?, 'N', 'A', ?, ?, 0, 0)", id, telefono, email);
    }

    @Test
    void laFichaSeBuscaPorEmailSinDistinguirMayusculasYSiNoPorTelefono() {

        familiar(1, "Ana@Example.com", "600000001");
        familiar(2, "otra@example.com", "+34 623 17 68 18");

        Familiar porEmail = familiarDao.obtenerPorEmailOTelefono("  ANA@example.COM ", null);
        Familiar porTelefono = familiarDao.obtenerPorEmailOTelefono("nadie@example.com", "623176818");

        assertThat(porEmail.getId()).isEqualTo(1L);
        assertThat(porTelefono.getId()).isEqualTo(2L);
    }

    @Test
    void elEmailMandaSobreElTelefono() {

        familiar(1, "uno@example.com", "623176818");
        familiar(2, "dos@example.com", "600000002");

        assertThat(familiarDao.obtenerPorEmailOTelefono("dos@example.com", "623176818").getId()).isEqualTo(2L);
    }

    @Test
    void sinCoincidenciaOConUnTelefonoVacioONuloNoDevuelveNada() {

        familiar(1, "uno@example.com", "623176818");

        assertThat(familiarDao.obtenerPorEmailOTelefono("otro@example.com", "699999999")).isNull();
        assertThat(familiarDao.obtenerPorEmailOTelefono("otro@example.com", null)).isNull();
        assertThat(familiarDao.obtenerPorEmailOTelefono("otro@example.com", "  ")).isNull();
        assertThat(familiarDao.obtenerPorEmailOTelefono(null, "123")).isNull();
    }
}
