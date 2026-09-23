package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacion;
import com.mikedev.mutxamelcf.model.RolApp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComunicacionDaoImplTest {

    @Test
    void guardarUsaTipoGrupalPorDefectoCuandoEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10L);

        Comunicacion comunicacion = new Comunicacion();
        comunicacion.setTitulo("Titulo");
        comunicacion.setContenido("Contenido");

        Long id = dao.guardar(comunicacion);

        assertThat(id).isEqualTo(10L);
        assertThat(comunicacion.getId()).isEqualTo(10L);
        verify(jdbcTemplate).update(anyString(), eq(10L), eq("Titulo"), eq("Contenido"), any(), any(), any(),
                eq("GRUPAL"));
    }

    @Test
    void guardarRespetaElTipoIndicado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(11L);

        Comunicacion comunicacion = new Comunicacion();
        comunicacion.setTipo("PRIVADA");

        dao.guardar(comunicacion);

        verify(jdbcTemplate).update(anyString(), eq(11L), any(), any(), any(), any(), any(), eq("PRIVADA"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorId(1L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaFechasPresentesYAusentes() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        ArgumentCaptor<RowMapper<Comunicacion>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(new Comunicacion()));
        dao.obtenerPorId(1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getString("TITULO")).thenReturn("Titulo");
        when(rs.getString("CONTENIDO")).thenReturn("Contenido");
        when(rs.getLong("USUARIO_AUTOR_ID")).thenReturn(2L);
        when(rs.getTimestamp("FECHA_CREACION")).thenReturn(Timestamp.valueOf("2026-01-01 10:00:00"));
        when(rs.getTimestamp("FECHA_PUBLICACION")).thenReturn(Timestamp.valueOf("2026-01-02 10:00:00"));
        when(rs.getInt("ACTIVA")).thenReturn(1);
        when(rs.getString("TIPO")).thenReturn("GRUPAL");

        Comunicacion conFechas = captor.getValue().mapRow(rs, 0);
        assertThat(conFechas.getFechaCreacion()).isNotNull();
        assertThat(conFechas.getFechaPublicacion()).isNotNull();

        when(rs.getTimestamp("FECHA_CREACION")).thenReturn(null);
        when(rs.getTimestamp("FECHA_PUBLICACION")).thenReturn(null);

        Comunicacion sinFechas = captor.getValue().mapRow(rs, 0);
        assertThat(sinFechas.getFechaCreacion()).isNull();
        assertThat(sinFechas.getFechaPublicacion()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodasDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodas()).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoFiltraPorEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerPorEquipo(1L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCategoriaFiltraPorCategoria() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("SENIOR"))).thenReturn(esperado);

        assertThat(dao.obtenerPorCategoria("SENIOR")).isSameAs(esperado);
    }

    @Test
    void guardarEquipoEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        dao.guardarEquipo(1L, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1L), eq(2L));
    }

    @Test
    void guardarCategoriaEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        dao.guardarCategoria(1L, "SENIOR");

        verify(jdbcTemplate).update(anyString(), eq(1L), eq("SENIOR"));
    }

    @Test
    void eliminarBorraEnLasCuatroTablas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        dao.eliminar(1L);

        verify(jdbcTemplate).update(eq("DELETE FROM COMUNICACION_EQUIPO WHERE COMUNICACION_ID = ?"), eq(1L));
        verify(jdbcTemplate).update(eq("DELETE FROM COMUNICACION_CATEGORIA WHERE COMUNICACION_ID = ?"), eq(1L));
        verify(jdbcTemplate).update(eq("DELETE FROM COMUNICACION_USUARIO WHERE COMUNICACION_ID = ?"), eq(1L));
        verify(jdbcTemplate).update(eq("DELETE FROM COMUNICACIONES WHERE ID = ?"), eq(1L));
    }

    @Test
    void entrenadorPuedeGestionarEquipoDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L))).thenReturn(1);

        assertThat(dao.entrenadorPuedeGestionarEquipo(1L, 2L)).isTrue();
    }

    @Test
    void existeEquipoDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);

        assertThat(dao.existeEquipo(1L)).isTrue();
    }

    @Test
    void existeCategoriaDevuelveFalseCuandoElCountEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("SENIOR"))).thenReturn(null);

        assertThat(dao.existeCategoria("SENIOR")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEquiposDeEntrenadorMapeaLosIds() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        ArgumentCaptor<RowMapper<Long>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(10L));

        assertThat(dao.obtenerEquiposDeEntrenador(1L)).containsExactly(10L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(20L);
        assertThat(captor.getValue().mapRow(rs, 0)).isEqualTo(20L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEquiposDeJugadorMapeaLosIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of(10L));

        assertThat(dao.obtenerEquiposDeJugador(1L)).containsExactly(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEquiposDeFamiliarMapeaLosIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of(10L));

        assertThat(dao.obtenerEquiposDeFamiliar(1L)).containsExactly(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerParaUsuarioDevuelveVacioSiNoTieneEquipos() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of());

        assertThat(dao.obtenerParaUsuario(1L)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerParaUsuarioDelegaEnObtenerPorEquiposYCategoriasCuandoTieneEquipos() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        // obtenerEquiposDeJugador y obtenerEquiposDeFamiliar usan la misma
        // firma de query; devolvemos un equipo en ambas llamadas
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of(5L));

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(esperado);

        assertThat(dao.obtenerParaUsuario(1L)).isSameAs(esperado);
    }

    @Test
    void obtenerPorEquiposDevuelveVacioSiLaListaEsNulaOVacia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        assertThat(dao.obtenerPorEquipos(null)).isEmpty();
        assertThat(dao.obtenerPorEquipos(List.of())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquiposConstruyeElInClauseCorrectamente() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(esperado);

        assertThat(dao.obtenerPorEquipos(List.of(1L, 2L))).isSameAs(esperado);
    }

    @Test
    void obtenerPorEquiposYCategoriasDevuelveVacioSiLaListaEsNulaOVacia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        assertThat(dao.obtenerPorEquiposYCategorias(null)).isEmpty();
        assertThat(dao.obtenerPorEquiposYCategorias(List.of())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerUsuariosDelEquipoDelegaEnQueryForList() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1L), eq(1L), eq(1L)))
                .thenReturn(List.of(2L, 3L));

        assertThat(dao.obtenerUsuariosDelEquipo(1L)).containsExactly(2L, 3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerUsuariosDeCategoriaDelegaEnQueryForList() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("SENIOR"), eq("SENIOR"), eq("SENIOR")))
                .thenReturn(List.of(2L));

        assertThat(dao.obtenerUsuariosDeCategoria("SENIOR")).containsExactly(2L);
    }

    @Test
    void guardarUsuarioEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        dao.guardarUsuario(1L, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1L), eq(2L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioDirectoFiltraPorUsuario() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerPorUsuarioDirecto(1L)).isSameAs(esperado);
    }

    @Test
    void usuarioPuedeVerDirectamenteDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L))).thenReturn(1);

        assertThat(dao.usuarioPuedeVerDirectamente(1L, 2L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerDestinatariosDirectosPermitidosPasaElUsuarioDoceVeces() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        ArgumentCaptor<RowMapper<Long>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L), eq(1L), eq(1L), eq(1L), eq(1L), eq(1L),
                eq(1L), eq(1L), eq(1L), eq(1L), eq(1L), eq(1L))).thenReturn(List.of(5L));

        assertThat(dao.obtenerDestinatariosDirectosPermitidos(1L)).containsExactly(5L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(9L);
        assertThat(captor.getValue().mapRow(rs, 0)).isEqualTo(9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEnviadasPorUsuarioFiltraPorAutor() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerEnviadasPorUsuario(1L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerDestinatariosDirectosDevuelveVacioSiNoHayPermitidos() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), (Object[]) any())).thenReturn(List.of());

        assertThat(dao.obtenerDestinatariosDirectos(1L)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerDestinatariosDirectosCompletaElRolDeCadaDestinatario() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        // 1a llamada: obtenerDestinatariosDirectosPermitidos (12 params) -> un id permitido
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(1L), eq(1L), eq(1L), eq(1L), eq(1L),
                eq(1L), eq(1L), eq(1L), eq(1L), eq(1L), eq(1L))).thenReturn(List.of(5L));

        DestinatarioComunicacion destinatario = new DestinatarioComunicacion(5L, "Juan", "Perez", null);

        // 2a llamada: la consulta de destinatarios con placeholders dinamicos (1 param)
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L))).thenReturn(List.of(destinatario));

        RolApp rol = new RolApp();
        rol.setCodigo("JUGADOR");
        when(rolAppDao.obtenerPorUsuario(5)).thenReturn(List.of(rol));

        List<DestinatarioComunicacion> resultado = dao.obtenerDestinatariosDirectos(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getRol()).isEqualTo("JUGADOR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerConversacionPasaLosCuatroParametrosEnElOrdenCorrecto() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        List<Comunicacion> esperado = List.of(new Comunicacion());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(2L), eq(2L), eq(1L)))
                .thenReturn(esperado);

        assertThat(dao.obtenerConversacion(1L, 2L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPrivadasDeUsuarioAnadeLaContraparte() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDao rolAppDao = mock(RolAppDao.class);
        ComunicacionDaoImpl dao = new ComunicacionDaoImpl(jdbcTemplate, rolAppDao);

        ArgumentCaptor<RowMapper<Comunicacion>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L), eq(1L), eq(1L))).thenReturn(List.of());

        dao.obtenerPrivadasDeUsuario(1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getString("TITULO")).thenReturn(null);
        when(rs.getString("CONTENIDO")).thenReturn("Hola");
        when(rs.getLong("USUARIO_AUTOR_ID")).thenReturn(1L);
        when(rs.getInt("ACTIVA")).thenReturn(1);
        when(rs.getString("TIPO")).thenReturn("PRIVADA");
        when(rs.getLong("CONTRAPARTE_ID")).thenReturn(2L);

        Comunicacion mapeada = captor.getValue().mapRow(rs, 0);
        assertThat(mapeada.getContraparteId()).isEqualTo(2L);
    }
}
