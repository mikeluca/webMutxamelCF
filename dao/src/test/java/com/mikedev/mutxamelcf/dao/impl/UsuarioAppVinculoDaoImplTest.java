package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.PersonaVinculable;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioAppVinculoDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerJugadoresSinCuentaMapeaNombreCompletoConAmbosApellidos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<PersonaVinculable>> captor = ArgumentCaptor.forClass(RowMapper.class);
        PersonaVinculable esperado = new PersonaVinculable(1L, "Juan Perez", "Senior A", null);
        when(jdbcTemplate.query(anyString(), captor.capture())).thenReturn(List.of(esperado));

        assertThat(dao.obtenerJugadoresSinCuenta()).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getString("NOMBRE")).thenReturn("Juan");
        when(rs.getString("APELLIDOS")).thenReturn("Perez");
        when(rs.getString("EQUIPO")).thenReturn("Senior A");

        PersonaVinculable mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getNombreCompleto()).isEqualTo("Juan Perez");
        assertThat(mapeado.getEquipo()).isEqualTo("Senior A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerFamiliaresSinCuentaMapeaCorrectamente() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<PersonaVinculable>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture())).thenReturn(List.of());

        assertThat(dao.obtenerFamiliaresSinCuenta()).isEmpty();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(2L);
        when(rs.getString("NOMBRE")).thenReturn(null);
        when(rs.getString("APELLIDOS")).thenReturn("Garcia");
        when(rs.getString("EMAIL")).thenReturn("ana@example.com");

        PersonaVinculable mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getNombreCompleto()).isEqualTo("Garcia");
        assertThat(mapeado.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerCuerpoTecnicoSinCuentaMapeaCorrectamente() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<PersonaVinculable>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture())).thenReturn(List.of());

        dao.obtenerCuerpoTecnicoSinCuenta();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getString("NOMBRE")).thenReturn("Ana");
        when(rs.getString("APELLIDOS")).thenReturn(null);
        when(rs.getString("EQUIPO")).thenReturn("Senior A");

        PersonaVinculable mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getNombreCompleto()).isEqualTo("Ana");
    }

    @Test
    void jugadorTieneCuentaDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);

        assertThat(dao.jugadorTieneCuenta(1L)).isTrue();
    }

    @Test
    void familiarTieneCuentaDevuelveFalseCuandoNoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);

        assertThat(dao.familiarTieneCuenta(1L)).isFalse();
    }

    @Test
    void cuerpoTecnicoTieneCuentaDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);

        assertThat(dao.cuerpoTecnicoTieneCuenta(1L)).isTrue();
    }

    @Test
    void vincularJugadorEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.vincularJugador(1, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2L));
    }

    @Test
    void vincularFamiliarEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.vincularFamiliar(1, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2L));
    }

    @Test
    void vincularCuerpoTecnicoEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.vincularCuerpoTecnico(1, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2L));
    }

    @Test
    void desvincularTodoBorraLosTresTiposDeVinculo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.desvincularTodo(1);

        verify(jdbcTemplate).update(eq("DELETE FROM USUARIOS_APP_JUGADORES WHERE USUARIO_APP_ID = ?"), eq(1));
        verify(jdbcTemplate).update(eq("DELETE FROM USUARIOS_APP_FAMILIARES WHERE USUARIO_APP_ID = ?"), eq(1));
        verify(jdbcTemplate).update(eq("DELETE FROM USUARIOS_APP_CUERPO_TECNICO WHERE USUARIO_APP_ID = ?"), eq(1));
    }

    @Test
    void desvincularJugadorEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.desvincularJugador(1, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2L));
    }

    @Test
    void desvincularFamiliarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.desvincularFamiliar(1, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2L));
    }

    @Test
    void desvincularCuerpoTecnicoEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        dao.desvincularCuerpoTecnico(1, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerVinculosDevuelveVacioCuandoNoHayNinguno() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of());

        assertThat(dao.obtenerVinculos(1)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerVinculosDevuelveJugadorCuandoExiste() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<ResultSetExtractor<VinculoUsuarioApp>> extractorCaptor = ArgumentCaptor
                .forClass(ResultSetExtractor.class);

        VinculoUsuarioApp jugador = new VinculoUsuarioApp("JUGADOR", 5L, "Juan Perez", "Senior A");

        // Primera llamada (obtenerVinculoJugador) devuelve el jugador; las siguientes, null/vacio
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), extractorCaptor.capture()))
                .thenReturn(jugador)
                .thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of());

        List<VinculoUsuarioApp> vinculos = dao.obtenerVinculos(1);

        assertThat(vinculos).containsExactly(jugador);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("ID")).thenReturn(5L);
        when(rs.getString("NOMBRE")).thenReturn("Juan");
        when(rs.getString("APELLIDOS")).thenReturn("Perez");
        when(rs.getString("EQUIPO")).thenReturn("Senior A");

        VinculoUsuarioApp extraido = extractorCaptor.getAllValues().get(0).extractData(rs);
        assertThat(extraido.getPersonaId()).isEqualTo(5L);

        when(rs.next()).thenReturn(false);
        assertThat(extractorCaptor.getAllValues().get(0).extractData(rs)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerVinculosDevuelveFamiliarConDetalleDeSusJugadores() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        VinculoUsuarioApp familiarSinDetalle = new VinculoUsuarioApp("FAMILIAR", 7L, "Ana Garcia");

        // 1a llamada (jugador) -> null; 2a llamada (familiar) -> el familiar
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenReturn(null)
                .thenReturn(familiarSinDetalle);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of("Pedro Garcia", "Luis Garcia"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of());

        List<VinculoUsuarioApp> vinculos = dao.obtenerVinculos(1);

        assertThat(vinculos).hasSize(1);
        assertThat(vinculos.get(0).getDetalle()).isEqualTo("Pedro Garcia, Luis Garcia");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerVinculosDevuelveEntrenadorConEquiposUnicos() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenReturn(null);

        VinculoUsuarioApp fila1 = new VinculoUsuarioApp("ENTRENADOR", 8L, "Carlos Ruiz", "Senior A");
        VinculoUsuarioApp fila2 = new VinculoUsuarioApp("ENTRENADOR", 8L, "Carlos Ruiz", "Senior B");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of(fila1, fila2));

        List<VinculoUsuarioApp> vinculos = dao.obtenerVinculos(1);

        assertThat(vinculos).hasSize(1);
        assertThat(vinculos.get(0).getDetalle()).isEqualTo("Senior A, Senior B");
    }

    @Test
    void obtenerNombrePersonaDevuelveNullParaTipoDesconocido() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        assertThat(dao.obtenerNombrePersona("OTRO", 1L)).isNull();
    }

    @Test
    void obtenerNombrePersonaDevuelveNullSiPersonaIdEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        assertThat(dao.obtenerNombrePersona("JUGADOR", null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerNombrePersonaConsultaLaTablaCorrectaPorTipo() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), pssCaptor.capture(), any(ResultSetExtractor.class)))
                .thenReturn("Juan Perez");

        assertThat(dao.obtenerNombrePersona("JUGADOR", 1L)).isEqualTo("Juan Perez");
        assertThat(sqlCaptor.getValue()).contains("JUGADORES");

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setLong(1, 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerNombrePersonaExtraeElNombreCuandoHayFila() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<ResultSetExtractor<String>> rseCaptor = ArgumentCaptor.forClass(ResultSetExtractor.class);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), rseCaptor.capture()))
                .thenReturn("Ana Garcia");

        dao.obtenerNombrePersona("FAMILIAR", 1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getString("NOMBRE")).thenReturn("Ana");
        when(rs.getString("APELLIDOS")).thenReturn("Garcia");

        assertThat(rseCaptor.getValue().extractData(rs)).isEqualTo("Ana Garcia");

        when(rs.next()).thenReturn(false);
        assertThat(rseCaptor.getValue().extractData(rs)).isNull();
    }

    @Test
    void obtenerEmailFamiliarDevuelveNullSiFamiliarIdEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        assertThat(dao.obtenerEmailFamiliar(null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEmailFamiliarConsultaPorId() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppVinculoDaoImpl dao = new UsuarioAppVinculoDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        ArgumentCaptor<ResultSetExtractor<String>> rseCaptor = ArgumentCaptor.forClass(ResultSetExtractor.class);
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), rseCaptor.capture())).thenReturn("ana@example.com");

        assertThat(dao.obtenerEmailFamiliar(1L)).isEqualTo("ana@example.com");

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setLong(1, 1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getString("EMAIL")).thenReturn("ana@example.com");
        assertThat(rseCaptor.getValue().extractData(rs)).isEqualTo("ana@example.com");

        when(rs.next()).thenReturn(false);
        assertThat(rseCaptor.getValue().extractData(rs)).isNull();
    }
}
