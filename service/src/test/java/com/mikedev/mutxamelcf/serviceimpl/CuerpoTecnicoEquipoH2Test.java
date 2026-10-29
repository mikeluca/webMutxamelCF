package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.ComunicacionDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.CuerpoTecnicoDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.EquipoDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.EquipoGestionDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.PerfilAppDaoImpl;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.FusionFichasDTO;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.SinEntrenadores;

/**
 * Un técnico puede llevar varios equipos (CUERPO_TECNICO_EQUIPO): permisos, destinatarios, recordatorios, puestos,
 * guardado de la ficha y fusión de fichas duplicadas, con un contexto Spring de verdad (transacciones sobre H2 en
 * modo Oracle) y los DAO reales. Datos inventados.
 *
 * <p>Equipos: 1 Alevín A, 2 Benjamín B, 3 Cadete A, 4 Prebenjamín A, 5 Prebenjamín B.
 * Fichas y cuentas de la app:
 * <ul>
 * <li>10 Marcos Ejemplo ("Entrenador"): equipos 1 y 2, mismo puesto en los dos (CTE.PUESTO NULL). Cuenta 200.</li>
 * <li>11 Lucía Prueba ("Entrenador"): equipo 3. Cuenta 201.</li>
 * <li>12 Nora Modelo ("Entrenador"): equipo 1 como "Segundo entrenador" y equipo 2 con el general. Cuenta 202.</li>
 * <li>13 Mario Doble ("Entrenador"): equipos 4 y 5. Cuenta 203, con rol ENTRENADOR y COORDINADOR.</li>
 * <li>14 y 15: la misma persona dada de alta dos veces en el equipo 3. Cuenta 204 vinculada a las dos.</li>
 * </ul>
 * Cuenta 300: jugador del equipo 1; 301: familiar de un jugador del equipo 2; 104: coordinador.
 */
class CuerpoTecnicoEquipoH2Test {

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        // Espía para poder hacer fallar una operación a mitad y comprobar que no queda nada a medias.
        @Bean
        CuerpoTecnicoDao cuerpoTecnicoDao(JdbcTemplate jdbc) {
            return spy(new CuerpoTecnicoDaoImpl(jdbc));
        }

        @Bean
        EquipoDao equipoDao(JdbcTemplate jdbc) {
            return new EquipoDaoImpl(jdbc);
        }

        @Bean
        CuerpoTecnicoService cuerpoTecnicoService(CuerpoTecnicoDao cuerpoTecnicoDao, EquipoDao equipoDao) {
            UsuarioAppVinculoDao vinculos = mock(UsuarioAppVinculoDao.class);
            return new CuerpoTecnicoServiceImpl(cuerpoTecnicoDao, vinculos, equipoDao);
        }
    }

    private AnnotationConfigApplicationContext contexto;
    private JdbcTemplate jdbc;
    private CuerpoTecnicoService servicio;
    private CuerpoTecnicoDao dao;
    private EquipoGestionDaoImpl gestion;
    private ComunicacionDaoImpl comunicacion;
    private PerfilAppDaoImpl perfil;

    @BeforeEach
    void setUp() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        jdbc = contexto.getBean(JdbcTemplate.class);
        servicio = contexto.getBean(CuerpoTecnicoService.class);
        dao = contexto.getBean(CuerpoTecnicoDao.class);

        jdbc.execute("""
                CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),
                    GRUPO VARCHAR2(10), ORDEN VARCHAR2(5), DEPORTE VARCHAR2(10), MODALIDAD VARCHAR2(5))""");
        jdbc.execute("CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,"
                + " FECHA_FIN DATE, ACTIVA NUMBER(1))");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearTransicion(jdbc);
        jdbc.execute("CREATE TABLE USUARIOS_APP (ID NUMBER PRIMARY KEY, ACTIVO NUMBER(1))");
        jdbc.execute("CREATE TABLE ROLES_APP (ID NUMBER PRIMARY KEY, CODIGO VARCHAR2(30))");
        jdbc.execute("CREATE TABLE USUARIOS_APP_ROLES (USUARIO_APP_ID NUMBER, ROL_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_JUGADORES (USUARIO_APP_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE FAMILIARES (ID NUMBER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE FAMILIARES_JUGADOR (FAMILIAR_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_FAMILIARES (USUARIO_APP_ID NUMBER, FAMILIAR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_CUERPO_TECNICO (USUARIO_APP_ID NUMBER, CUERPO_TECNICO_ID NUMBER)");

        equipo(1, "Alevín A", "Alevín");
        equipo(2, "Benjamín B", "Benjamín");
        equipo(3, "Cadete A", "Cadete");
        equipo(4, "Prebenjamín A", "Prebenjamín");
        equipo(5, "Prebenjamín B", "Prebenjamín");

        for (int rol = 1; rol <= 5; rol++) {
            jdbc.update("INSERT INTO ROLES_APP VALUES (?, ?)", rol,
                    new String[] { "", "JUGADOR", "FAMILIAR", "ENTRENADOR", "COORDINADOR", "ADMIN_APP" }[rol]);
        }
        for (int usuario : new int[] { 104, 200, 201, 202, 203, 204, 210, 211, 212, 300, 301 }) {
            jdbc.update("INSERT INTO USUARIOS_APP VALUES (?, 1)", usuario);
        }
        for (int entrenador : new int[] { 200, 201, 202, 203, 204 }) {
            jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (?, 3)", entrenador);
        }
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (203, 4)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (104, 4)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (300, 1)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (301, 2)");

        ficha(10, "Marcos", "Ejemplo", "Entrenador");
        ficha(11, "Lucía", "Prueba", "Entrenador");
        ficha(12, "Nora", "Modelo", "Entrenador");
        ficha(13, "Mario", "Doble", "Entrenador");
        ficha(14, "Pedro", "Repetido", "Entrenador");
        ficha(15, "Pedro", "Repetido", "Entrenador");
        equipoDeFicha(10, 1, null);
        equipoDeFicha(10, 2, null);
        equipoDeFicha(11, 3, null);
        equipoDeFicha(12, 1, "Segundo entrenador");
        equipoDeFicha(12, 2, null);
        equipoDeFicha(13, 4, null);
        equipoDeFicha(13, 5, null);
        equipoDeFicha(14, 3, null);
        equipoDeFicha(15, 3, null);
        vinculo(200, 10);
        vinculo(201, 11);
        vinculo(202, 12);
        vinculo(203, 13);
        vinculo(204, 14);
        vinculo(204, 15);

        // Un jugador del equipo 1 (cuenta 300) y la familia de un jugador del equipo 2 (cuenta 301)
        jdbc.update("""
                INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, EQUIPO_ID) VALUES (1, 'Jugador1', 'Inventado', 1)""");
        jdbc.update("""
                INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, EQUIPO_ID) VALUES (2, 'Jugador2', 'Inventado', 2)""");
        jdbc.update("INSERT INTO USUARIOS_APP_JUGADORES VALUES (300, 1)");
        jdbc.update("INSERT INTO FAMILIARES VALUES (50)");
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (50, 2)");
        jdbc.update("INSERT INTO USUARIOS_APP_FAMILIARES VALUES (301, 50)");

        gestion = new EquipoGestionDaoImpl(jdbc);
        comunicacion = new ComunicacionDaoImpl(jdbc, mock(RolAppDao.class));
        perfil = new PerfilAppDaoImpl(jdbc);
    }

    @AfterEach
    void tearDown() {
        contexto.close();
    }

    // ---- datos ----------------------------------------------------------------------------

    private void equipo(long id, String nombre, String categoria) {
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA, GRUPO, ORDEN, DEPORTE) VALUES (?, ?, ?, 'A', 'X', 'F')",
                id, nombre, categoria);
    }

    /** CUERPO_TECNICO.EQUIPO queda con un nombre que no existe: ninguna consulta debe depender de esa copia. */
    private void ficha(long id, String nombre, String apellidos, String puesto) {
        jdbc.update("INSERT INTO CUERPO_TECNICO (ID, NOMBRE, APELLIDOS, PUESTO, EQUIPO, CATEGORIA, DEPORTE)"
                + " VALUES (?, ?, ?, ?, 'Equipo que ya no existe', 'Antigua', 'F')", id, nombre, apellidos, puesto);
    }

    private void equipoDeFicha(long ficha, long equipo, String puesto) {
        jdbc.update("INSERT INTO CUERPO_TECNICO_EQUIPO (CUERPO_TECNICO_ID, EQUIPO_ID, PUESTO) VALUES (?, ?, ?)", ficha,
                equipo, puesto);
    }

    private void vinculo(long usuario, long ficha) {
        jdbc.update("INSERT INTO USUARIOS_APP_CUERPO_TECNICO VALUES (?, ?)", usuario, ficha);
    }

    private List<Long> equiposDe(long ficha) {
        return jdbc.queryForList("SELECT EQUIPO_ID FROM CUERPO_TECNICO_EQUIPO WHERE CUERPO_TECNICO_ID = ?"
                + " ORDER BY EQUIPO_ID", Long.class, ficha);
    }

    private String puestoEn(long ficha, long equipo) {
        return jdbc.queryForObject("SELECT PUESTO FROM CUERPO_TECNICO_EQUIPO WHERE CUERPO_TECNICO_ID = ? AND EQUIPO_ID = ?",
                String.class, ficha, equipo);
    }

    private int total(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    // ---- permisos de la app ---------------------------------------------------------------------

    @Test
    void unTecnicoConDosEquiposLosGestionaLosDosYNingunMas() {
        assertThat(gestion.puedeGestionarEquipo(200L, 1L)).isTrue();
        assertThat(gestion.puedeGestionarEquipo(200L, 2L)).isTrue();
        assertThat(gestion.puedeGestionarEquipo(200L, 3L)).isFalse();

        assertThat(comunicacion.entrenadorPuedeGestionarEquipo(200L, 1L)).isTrue();
        assertThat(comunicacion.entrenadorPuedeGestionarEquipo(200L, 2L)).isTrue();
        assertThat(comunicacion.entrenadorPuedeGestionarEquipo(200L, 3L)).isFalse();

        assertThat(comunicacion.obtenerEquiposDeEntrenador(200L)).containsExactly(1L, 2L);
        assertThat(perfil.obtenerEquiposPorUsuario(200)).extracting(Equipo::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void sinNingunEquipoNoGestionaNada() {
        jdbc.update("DELETE FROM CUERPO_TECNICO_EQUIPO WHERE CUERPO_TECNICO_ID = 11");

        assertThat(gestion.puedeGestionarEquipo(201L, 3L)).isFalse();
        assertThat(comunicacion.obtenerEquiposDeEntrenador(201L)).isEmpty();
        assertThat(perfil.obtenerEquiposPorUsuario(201)).isEmpty();
        // Sigue teniendo ficha y nombre en el perfil
        assertThat(perfil.obtenerCuerpoTecnicoPorUsuario(201)).hasSize(1);
    }

    @Test
    void renombrarUnEquipoNoRompeNadaDelEntrenador() {
        jdbc.update("UPDATE EQUIPO SET NOMBRE = 'Alevín Estrella' WHERE ID = 1");

        assertThat(gestion.puedeGestionarEquipo(200L, 1L)).isTrue();
        assertThat(comunicacion.obtenerEquiposDeEntrenador(200L)).containsExactly(1L, 2L);
        assertThat(gestion.obtenerEntrenadoresPorEquipo(1L)).containsExactlyInAnyOrder(200L, 202L);
        assertThat(perfil.obtenerEquiposPorUsuario(200)).extracting(Equipo::getNombre)
                .containsExactlyInAnyOrder("Alevín Estrella", "Benjamín B");
        assertThat(servicio.obtenerCuerpoTecnicoPublicoPorEquipoId(1L)).extracting(CuerpoTecnicoPublicDTO::getEquipo)
                .containsOnly("Alevín Estrella");
    }

    // ---- duplicados ----------------------------------------------------------------------------

    @Test
    void unTecnicoConDosEquiposApareceUnaSolaVezEnLosEntrenadoresYDestinatarios() {
        // Entrenadores del equipo 2: Marcos (también lleva el 1) y Nora (también lleva el 1), cada uno una vez
        assertThat(gestion.obtenerEntrenadoresPorEquipo(2L)).containsExactlyInAnyOrder(200L, 202L);
        // Si la misma persona está dada de alta dos veces en el mismo equipo, la cuenta sale una vez
        assertThat(gestion.obtenerEntrenadoresPorEquipo(3L)).containsExactlyInAnyOrder(201L, 204L);

        assertThat(comunicacion.obtenerUsuariosDelEquipo(1L)).containsExactlyInAnyOrder(300L, 200L, 202L);
        assertThat(Collections.frequency(comunicacion.obtenerUsuariosDelEquipo(2L), 200L)).isEqualTo(1);
        assertThat(Collections.frequency(comunicacion.obtenerUsuariosDeCategoria("Prebenjamín"), 203L)).isEqualTo(1);

        // El jugador (300) puede escribir a los entrenadores de su equipo: Marcos y Nora, una vez cada uno
        List<Long> destinatarios = comunicacion.obtenerDestinatariosDirectosPermitidos(300L);
        assertThat(Collections.frequency(destinatarios, 200L)).isEqualTo(1);
        assertThat(Collections.frequency(destinatarios, 202L)).isEqualTo(1);
        assertThat(destinatarios).doesNotContain(201L);

        // La familia (301, equipo 2) también
        List<Long> deLaFamilia = comunicacion.obtenerDestinatariosDirectosPermitidos(301L);
        assertThat(Collections.frequency(deLaFamilia, 200L)).isEqualTo(1);

        // Y el entrenador puede escribir al jugador del equipo 1 y a la familia del equipo 2, una vez cada uno
        List<Long> deMarcos = comunicacion.obtenerDestinatariosDirectosPermitidos(200L);
        assertThat(Collections.frequency(deMarcos, 300L)).isEqualTo(1);
        assertThat(Collections.frequency(deMarcos, 301L)).isEqualTo(1);
    }

    private RecordatorioEntrenadorServiceImpl recordatorios(List<Long>[] destinatarios, long equipoId) {
        com.mikedev.mutxamelcf.dao.PartidoDao partidoDao = mock(com.mikedev.mutxamelcf.dao.PartidoDao.class);
        Partido partido = new Partido();
        partido.setId(900L);
        partido.setEquipoId(equipoId);
        partido.setRival("Rival Inventado");
        partido.setDia(java.sql.Date.valueOf("2026-11-01"));
        when(partidoDao.obtenerPorId(900L)).thenReturn(partido);

        ComunicacionService comunicacionService = mock(ComunicacionService.class);
        org.mockito.Mockito.doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            List<Long> lista = invocacion.getArgument(1);
            destinatarios[0] = lista;
            return null;
        }).when(comunicacionService).crearPrivada(any(), any(), anyLong(), any());

        return new RecordatorioEntrenadorServiceImpl(partidoDao, mock(com.mikedev.mutxamelcf.dao.ConvocatoriaDao.class),
                new EquipoGestionDaoImpl(jdbc), comunicacionService,
                Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("Europe/Madrid")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void elRecordatorioAvisaUnaVezACadaEntrenadorAunqueLleveVariosEquipos() {
        List<Long>[] destinatarios = new List[1];
        RecordatorioEntrenadorServiceImpl recordatorio = recordatorios(destinatarios, 1L);

        var enviado = recordatorio.enviar(104L, 900L, "CONVOCATORIA");

        // Equipo 1: Marcos y Nora, cada uno una vez
        assertThat(enviado.destinatarios()).isEqualTo(2);
        assertThat(destinatarios[0]).containsExactlyInAnyOrder(200L, 202L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void quienLlevaDosEquiposYPideElRecordatorioCuentaComoUnoSoloParaUnicoEntrenador() {
        // Mario (203) es entrenador Y coordinador y lleva los equipos 4 y 5; es el único entrenador del 4
        List<Long>[] destinatarios = new List[1];
        RecordatorioEntrenadorServiceImpl recordatorio = recordatorios(destinatarios, 4L);

        assertThat(gestion.obtenerEntrenadoresPorEquipo(4L)).containsExactly(203L);
        assertThatThrownBy(() -> recordatorio.enviar(203L, 900L, "CONVOCATORIA"))
                .isInstanceOfSatisfying(SinEntrenadores.class,
                        e -> assertThat(e.codigo()).isEqualTo("UNICO_ENTRENADOR"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unEquipoSinCuerpoTecnicoDaSinEntrenadores() {
        jdbc.update("DELETE FROM CUERPO_TECNICO_EQUIPO WHERE EQUIPO_ID = 4");
        List<Long>[] destinatarios = new List[1];
        RecordatorioEntrenadorServiceImpl recordatorio = recordatorios(destinatarios, 4L);

        assertThatThrownBy(() -> recordatorio.enviar(104L, 900L, "CONVOCATORIA"))
                .isInstanceOfSatisfying(SinEntrenadores.class,
                        e -> assertThat(e.codigo()).isEqualTo("SIN_ENTRENADORES"));
    }

    // ---- puestos ------------------------------------------------------------------------------

    @Test
    void elMismoPuestoEnVariosEquiposSaleIgualEnTodosConPuestoDeEquipoNulo() {
        // Marcos: "Entrenador" en Alevín A y en Benjamín B, sin puesto propio en la tabla intermedia
        assertThat(puestoEn(10, 1)).isNull();
        assertThat(puestoEn(10, 2)).isNull();

        CuerpoTecnicoDTO marcos = servicio.obtenerCuerpoTecnicoPorId(10L);
        assertThat(marcos.getEquipos()).extracting(CuerpoTecnicoEquipoDTO::equipoNombre, CuerpoTecnicoEquipoDTO::puestoMostrado)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Alevín A", "Entrenador"),
                        org.assertj.core.groups.Tuple.tuple("Benjamín B", "Entrenador"));

        assertThat(servicio.obtenerCuerpoTecnicoPublicoPorEquipoId(1L)).extracting(CuerpoTecnicoPublicDTO::getNombre,
                CuerpoTecnicoPublicDTO::getPuesto).contains(org.assertj.core.groups.Tuple.tuple("Marcos", "Entrenador"));
        assertThat(servicio.obtenerCuerpoTecnicoPublicoPorEquipoId(2L)).extracting(CuerpoTecnicoPublicDTO::getNombre,
                CuerpoTecnicoPublicDTO::getPuesto).contains(org.assertj.core.groups.Tuple.tuple("Marcos", "Entrenador"));
    }

    @Test
    void unPuestoDistintoEnUnSoloEquipoSaleAhiYElGeneralEnElResto() {
        // Nora: "Segundo entrenador" en Alevín A y el general ("Entrenador") en Benjamín B
        assertThat(servicio.obtenerCuerpoTecnicoPublicoPorEquipoId(1L))
                .filteredOn(p -> p.getNombre().equals("Nora")).extracting(CuerpoTecnicoPublicDTO::getPuesto)
                .containsExactly("Segundo entrenador");
        assertThat(servicio.obtenerCuerpoTecnicoPublicoPorEquipoId(2L))
                .filteredOn(p -> p.getNombre().equals("Nora")).extracting(CuerpoTecnicoPublicDTO::getPuesto)
                .containsExactly("Entrenador");

        // En las listas por nombre de equipo y por categoría también
        assertThat(servicio.obtenerCuerpoTecnicoPorEquipo("Alevín A"))
                .filteredOn(p -> p.getNombre().equals("Nora")).extracting(CuerpoTecnicoDTO::getPuesto)
                .containsExactly("Segundo entrenador");
        assertThat(servicio.obtenerCuerpoTecnicoPorCategoria("Benjamín"))
                .filteredOn(p -> p.getNombre().equals("Nora")).extracting(CuerpoTecnicoDTO::getPuesto)
                .containsExactly("Entrenador");
    }

    // ---- guardar la ficha y sus equipos -------------------------------------------------------------

    private CuerpoTecnicoDTO dto(Long id, String nombre, String puesto, CuerpoTecnicoEquipoDTO... equipos) {
        CuerpoTecnicoDTO d = new CuerpoTecnicoDTO();
        d.setId(id);
        d.setNombre(nombre);
        d.setApellidos("Inventado");
        d.setPuesto(puesto);
        d.setEquipos(List.of(equipos));
        return d;
    }

    private static CuerpoTecnicoEquipoDTO en(long equipoId, String puesto) {
        return new CuerpoTecnicoEquipoDTO(equipoId, null, null, puesto, null);
    }

    private long idDe(String nombre) {
        return jdbc.queryForObject("SELECT ID FROM CUERPO_TECNICO WHERE NOMBRE = ?", Long.class, nombre);
    }

    @Test
    void guardarUnaFichaNuevaConDosEquiposYElMismoPuestoGuardaNull() {
        assertThat(servicio.guardarCuerpoTecnico(dto(null, "Nuevo", "Entrenador", en(1, null), en(2, "  entrenador ")))).isTrue();

        long id = idDe("Nuevo");
        assertThat(equiposDe(id)).containsExactly(1L, 2L);
        assertThat(puestoEn(id, 1)).isNull();
        assertThat(puestoEn(id, 2)).isNull();    // igual que el general (sin distinguir mayúsculas ni espacios)
        // La copia informativa es el primer equipo por nombre
        assertThat(jdbc.queryForMap("SELECT EQUIPO, CATEGORIA FROM CUERPO_TECNICO WHERE ID = ?", id))
                .containsEntry("EQUIPO", "Alevín A").containsEntry("CATEGORIA", "Alevín");
    }

    @Test
    void guardarConUnPuestoDistintoEnUnEquipoLoGuardaSoloAhi() {
        servicio.guardarCuerpoTecnico(dto(null, "Otro", "Entrenador", en(1, "Delegado"), en(2, "")));

        long id = idDe("Otro");
        assertThat(puestoEn(id, 1)).isEqualTo("Delegado");
        assertThat(puestoEn(id, 2)).isNull();
    }

    @Test
    void editarLaFichaAnadeQuitaYCambiaElPuestoDeSusEquipos() {
        // Marcos: equipos 1 y 2. Se quita el 2, se añade el 3 y el 1 pasa a "Segundo entrenador"
        servicio.guardarCuerpoTecnico(dto(10L, "Marcos", "Entrenador", en(1, "Segundo entrenador"), en(3, null)));

        assertThat(equiposDe(10)).containsExactly(1L, 3L);
        assertThat(puestoEn(10, 1)).isEqualTo("Segundo entrenador");
        assertThat(puestoEn(10, 3)).isNull();
        assertThat(total("CUERPO_TECNICO")).isEqualTo(6);

        // Y volver a dejar el mismo puesto que el general lo devuelve a NULL
        servicio.guardarCuerpoTecnico(dto(10L, "Marcos", "Entrenador", en(1, "Entrenador"), en(3, null)));
        assertThat(puestoEn(10, 1)).isNull();
    }

    @Test
    void guardarSinNingunEquipoEsValido() {
        // Por ejemplo, un coordinador
        assertThat(servicio.guardarCuerpoTecnico(dto(null, "Coord", "Coordinador"))).isTrue();

        long id = idDe("Coord");
        assertThat(equiposDe(id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT EQUIPO FROM CUERPO_TECNICO WHERE ID = ?", String.class, id)).isNull();

        // Y quitar todos los equipos de una ficha existente también
        servicio.guardarCuerpoTecnico(dto(11L, "Lucía", "Entrenador"));
        assertThat(equiposDe(11)).isEmpty();
    }

    @Test
    void unEquipoRepetidoEnLaListaSeGuardaUnaVez() {
        servicio.guardarCuerpoTecnico(dto(null, "Repe", "Entrenador", en(1, null), en(1, "Delegado")));

        assertThat(equiposDe(idDe("Repe"))).containsExactly(1L);
    }

    @Test
    void unEquipoQueNoExisteNoGuardaNada() {
        assertThatThrownBy(() -> servicio.guardarCuerpoTecnico(dto(null, "Mal", "Entrenador", en(1, null), en(99, null))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(total("CUERPO_TECNICO")).isEqualTo(6);
    }

    @Test
    void siFallaGuardarLosEquiposTampocoQuedaLaFicha() {
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(dao).reemplazarEquipos(anyLong(), any());

        assertThatThrownBy(() -> servicio.guardarCuerpoTecnico(dto(null, "Falla", "Entrenador", en(1, null))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CUERPO_TECNICO WHERE NOMBRE = 'Falla'", Integer.class)).isZero();
    }

    @Test
    void borrarUnaFichaBorraTambienSusEquipos() {
        servicio.eliminarCuerpoTecnico(13L);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CUERPO_TECNICO WHERE ID = 13", Integer.class)).isZero();
        assertThat(equiposDe(13)).isEmpty();
    }

    @Test
    void elListadoTrae_losEquiposDeCadaFichaEnUnaSolaConsulta() {
        Map<Long, CuerpoTecnicoDTO> porId = new java.util.HashMap<>();
        servicio.obtenerTodos().forEach(c -> porId.put(c.getId(), c));

        assertThat(porId).hasSize(6);
        assertThat(porId.get(10L).getEquipos()).hasSize(2);
        assertThat(porId.get(11L).getEquipos()).hasSize(1);
    }

    @Test
    void unEquipoConTecnicosSeDetectaYUnoSinTecnicosNo() {
        assertThat(servicio.equipoTieneCuerpoTecnico(1L)).isTrue();
        assertThat(servicio.equipoTieneCuerpoTecnico(5L)).isTrue();

        jdbc.update("DELETE FROM CUERPO_TECNICO_EQUIPO WHERE EQUIPO_ID = 5");
        assertThat(servicio.equipoTieneCuerpoTecnico(5L)).isFalse();
    }

    // ---- fusionar fichas ------------------------------------------------------------------------------

    @Test
    void fusionarPasaLosEquiposSinRepetirYMantieneElDeLaConservada() {
        // Conservada: Marcos (10) con equipos 1 y 2. Duplicadas: Lucía (11, equipo 3) y Nora (12, equipos 1 y 2)
        servicio.fusionarFichas(10L, List.of(11L, 12L));

        assertThat(equiposDe(10)).containsExactly(1L, 2L, 3L);
        // El equipo que ya llevaba se mantiene como estaba (sin puesto propio), aunque Nora tuviera otro en el 1
        assertThat(puestoEn(10, 1)).isNull();
        assertThat(puestoEn(10, 3)).isNull();     // el mismo puesto que el general de la conservada
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CUERPO_TECNICO WHERE ID IN (11, 12)", Integer.class)).isZero();
        assertThat(equiposDe(11)).isEmpty();
        assertThat(equiposDe(12)).isEmpty();
        // La ficha conserva su nombre y su puesto general
        assertThat(jdbc.queryForMap("SELECT NOMBRE, APELLIDOS, PUESTO FROM CUERPO_TECNICO WHERE ID = 10"))
                .containsEntry("NOMBRE", "Marcos").containsEntry("APELLIDOS", "Ejemplo")
                .containsEntry("PUESTO", "Entrenador");
    }

    @Test
    void siElPuestoDeLaDuplicadaEsOtroSeGuardaComoPuestoDeEsosEquipos() {
        jdbc.update("UPDATE CUERPO_TECNICO SET PUESTO = 'Delegado' WHERE ID = 11");
        jdbc.update("UPDATE CUERPO_TECNICO_EQUIPO SET PUESTO = 'Preparador' WHERE CUERPO_TECNICO_ID = 12 AND EQUIPO_ID = 1");
        // La conservada es Lucía-como-Entrenador (11, equipo 3); duplicadas: Nora (12) y Marcos (10)
        jdbc.update("UPDATE CUERPO_TECNICO SET PUESTO = 'Entrenador' WHERE ID = 11");
        jdbc.update("UPDATE CUERPO_TECNICO SET PUESTO = 'Delegado' WHERE ID = 10");

        servicio.fusionarFichas(11L, List.of(10L, 12L));

        assertThat(equiposDe(11)).containsExactly(1L, 2L, 3L);
        // Marcos (puesto general ahora "Delegado") aporta el equipo 1 y 2 con "Delegado" por ser distinto del
        // general de la conservada ("Entrenador"); pero el equipo 1 lo trae el primero de la lista, Marcos
        assertThat(puestoEn(11, 1)).isEqualTo("Delegado");
        assertThat(puestoEn(11, 2)).isEqualTo("Delegado");
        assertThat(puestoEn(11, 3)).isNull();
    }

    @Test
    void fusionarMueveLosVinculosDeLaAppSinRepetir() {
        vinculo(210, 11);
        vinculo(211, 12);
        vinculo(212, 10);
        vinculo(212, 12);    // la misma cuenta vinculada a la conservada y a una duplicada

        servicio.fusionarFichas(10L, List.of(11L, 12L));

        List<Map<String, Object>> vinculos = jdbc.queryForList(
                "SELECT USUARIO_APP_ID, CUERPO_TECNICO_ID FROM USUARIOS_APP_CUERPO_TECNICO ORDER BY USUARIO_APP_ID");
        // Todas las cuentas de las fichas fusionadas ahora apuntan a la 10, cada una una vez
        assertThat(vinculos).allSatisfy(v -> assertThat(((Number) v.get("CUERPO_TECNICO_ID")).longValue())
                .isIn(10L, 13L, 14L, 15L));
        assertThat(jdbc.queryForList("SELECT USUARIO_APP_ID FROM USUARIOS_APP_CUERPO_TECNICO WHERE CUERPO_TECNICO_ID = 10"
                + " ORDER BY USUARIO_APP_ID", Long.class)).containsExactly(200L, 201L, 202L, 210L, 211L, 212L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM USUARIOS_APP_CUERPO_TECNICO WHERE CUERPO_TECNICO_ID IN (11, 12)",
                Integer.class)).isZero();
    }

    @Test
    void fusionarLaMismaPersonaDadaDeAltaDosVeces() {
        servicio.fusionarFichas(14L, List.of(15L));

        assertThat(equiposDe(14)).containsExactly(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CUERPO_TECNICO WHERE ID = 15", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT CUERPO_TECNICO_ID FROM USUARIOS_APP_CUERPO_TECNICO WHERE USUARIO_APP_ID = 204",
                Long.class)).containsExactly(14L);
        // Y ya cuenta como un solo entrenador del equipo
        assertThat(gestion.obtenerEntrenadoresPorEquipo(3L)).containsExactlyInAnyOrder(201L, 204L);
    }

    @Test
    void elResumenDiceQueVaAPasarYNoCambiaNada() {
        vinculo(210, 11);

        FusionFichasDTO resumen = servicio.resumenFusion(10L, List.of(11L, 12L));

        assertThat(resumen.conservada()).isEqualTo("Marcos Ejemplo");
        assertThat(resumen.duplicadas()).containsExactly("Lucía Prueba", "Nora Modelo");
        assertThat(resumen.equiposQueSePasan()).containsExactly("Cadete A");
        assertThat(resumen.equiposYaPresentes()).containsExactlyInAnyOrder("Alevín A", "Benjamín B");
        assertThat(resumen.vinculosAMover()).isEqualTo(3);    // 201 y 210 en la ficha 11 (Lucía) y 202 en la 12 (Nora)

        assertThat(total("CUERPO_TECNICO")).isEqualTo(6);
        assertThat(equiposDe(10)).containsExactly(1L, 2L);
    }

    @Test
    void nuncaSeFusionaUnaFichaConsigoMisma() {
        assertThatThrownBy(() -> servicio.fusionarFichas(10L, List.of(10L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("consigo misma");
        assertThatThrownBy(() -> servicio.fusionarFichas(10L, List.of(11L, 10L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.resumenFusion(10L, List.of(10L)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(total("CUERPO_TECNICO")).isEqualTo(6);
    }

    @Test
    void sinDuplicadasOConFichasQueNoExistenFalla() {
        assertThatThrownBy(() -> servicio.fusionarFichas(10L, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.fusionarFichas(10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.fusionarFichas(10L, List.of(99L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.fusionarFichas(99L, List.of(11L))).isInstanceOf(IllegalArgumentException.class);

        assertThat(total("CUERPO_TECNICO")).isEqualTo(6);
    }

    @Test
    void siAlgoFallaAMitadDeLaFusionNoCambiaNada() {
        vinculo(210, 11);
        vinculo(211, 12);
        List<Map<String, Object>> fichasAntes = jdbc.queryForList("SELECT ID, NOMBRE, PUESTO, EQUIPO FROM CUERPO_TECNICO ORDER BY ID");
        List<Map<String, Object>> equiposAntes = jdbc.queryForList(
                "SELECT CUERPO_TECNICO_ID, EQUIPO_ID, PUESTO FROM CUERPO_TECNICO_EQUIPO ORDER BY CUERPO_TECNICO_ID, EQUIPO_ID");
        List<Map<String, Object>> vinculosAntes = jdbc.queryForList(
                "SELECT USUARIO_APP_ID, CUERPO_TECNICO_ID FROM USUARIOS_APP_CUERPO_TECNICO ORDER BY USUARIO_APP_ID, CUERPO_TECNICO_ID");
        // Falla al borrar la segunda duplicada: ya se han pasado los equipos y los vínculos de la primera
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(dao).eliminar(12L);

        assertThatThrownBy(() -> servicio.fusionarFichas(10L, List.of(11L, 12L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForList("SELECT ID, NOMBRE, PUESTO, EQUIPO FROM CUERPO_TECNICO ORDER BY ID")).isEqualTo(fichasAntes);
        assertThat(jdbc.queryForList(
                "SELECT CUERPO_TECNICO_ID, EQUIPO_ID, PUESTO FROM CUERPO_TECNICO_EQUIPO ORDER BY CUERPO_TECNICO_ID, EQUIPO_ID"))
                .isEqualTo(equiposAntes);
        assertThat(jdbc.queryForList(
                "SELECT USUARIO_APP_ID, CUERPO_TECNICO_ID FROM USUARIOS_APP_CUERPO_TECNICO ORDER BY USUARIO_APP_ID, CUERPO_TECNICO_ID"))
                .isEqualTo(vinculosAntes);
    }
}
