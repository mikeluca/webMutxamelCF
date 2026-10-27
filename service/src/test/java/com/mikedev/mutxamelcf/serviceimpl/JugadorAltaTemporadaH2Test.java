package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.JugadorDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JugadorTemporadaDaoImpl;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;

/** El alta de un jugador (con el esquema de PRO, sin DNI) crea su fila de JUGADOR_TEMPORADA de la temporada activa. */
class JugadorAltaTemporadaH2Test {

    private JdbcTemplate jdbc;
    private JugadorServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),"
                + " DEPORTE VARCHAR2(10), ORDEN VARCHAR2(5))");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA) VALUES (1, 'Cadete A', 'Cadete')");

        jdbc.execute("CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,"
                + " FECHA_FIN DATE, ACTIVA NUMBER(1))");
        jdbc.update("INSERT INTO TEMPORADAS VALUES (5, '2026-27', DATE '2026-09-01', DATE '2027-06-30', 1)");

        TemporadaDao temporadaDao = mock(TemporadaDao.class);
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(
                new Temporada(5L, "2026-27", Date.valueOf("2026-09-01"), Date.valueOf("2027-06-30"), 1));

        service = new JugadorServiceImpl(new JugadorDaoImpl(jdbc), mock(FamiliarJugadorService.class),
                mock(UsuarioAppVinculoDao.class), mock(CuotaJugadorService.class), mock(ConvocatoriaJugadorDao.class),
                mock(EntrenamientoAsistenciaDao.class), null, null, null, null, mock(EquipoDao.class), temporadaDao,
                new JugadorTemporadaDaoImpl(jdbc), java.time.Clock.systemDefaultZone());
    }

    @Test
    void altaConFotoYEquipoCreaLaFilaDeLaTemporadaActiva() {
        JugadorDTO dto = new JugadorDTO();
        dto.setNombre("Pablo");
        dto.setApellidos("Garcia");
        dto.setCategoria("Cadete");
        dto.setDeporte("Futbol 11");
        dto.setEquipo("Cadete A");
        dto.setEquipoId(1L);
        dto.setDorsal(9);
        dto.setPosicion("DEL");
        dto.setAnioNacimiento(2011);
        dto.setFoto(new byte[] { 1, 2 });

        assertThat(service.guardarJugador(dto)).isTrue();

        Long id = jdbc.queryForObject("SELECT ID FROM JUGADORES", Long.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ? AND TEMPORADA_ID = 5",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT EQUIPO_ID FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", Long.class, id))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT DORSAL FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", Integer.class, id))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT BAJA FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", Integer.class, id))
                .isZero();
    }
}
