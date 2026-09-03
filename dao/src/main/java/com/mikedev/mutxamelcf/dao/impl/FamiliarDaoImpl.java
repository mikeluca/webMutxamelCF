package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.FamiliarDao;
import com.mikedev.mutxamelcf.model.Familiar;

@Repository
public class FamiliarDaoImpl implements FamiliarDao {

    private static final Logger logger = LoggerFactory.getLogger(FamiliarDaoImpl.class);

    private static final RowMapper<Familiar> FAMILIAR_ROW_MAPPER = FamiliarDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public FamiliarDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Familiar mapRow(ResultSet rs, int rowNum) throws SQLException {
        Familiar familiar = new Familiar();
        familiar.setId(rs.getLong("ID"));
        familiar.setNombre(rs.getString("NOMBRE"));
        familiar.setApellidos(rs.getString("APELLIDOS"));
        familiar.setTelefono(rs.getString("TELEFONO"));
        familiar.setEmail(rs.getString("EMAIL"));
        familiar.setRecibeInfoClub(rs.getInt("RECIBE_INFO_CLUB"));
        familiar.setWhatsappActivo(rs.getInt("WHATSAPP_ACTIVO"));
        return familiar;
    }

    @Override
    public boolean guardarFamiliar(Familiar familiar) {
        logger.debug("Inicio guardarFamiliar: id={}, nombre={}", familiar != null ? familiar.getId() : null,
                familiar != null ? familiar.getNombre() : null);

        if (familiar == null) {
            return false;
        }

        boolean resultado;
        if (familiar.getId() != null && obtenerPorId(familiar.getId()) != null) {
            resultado = actualizarFamiliar(familiar);
        } else {
            resultado = insertarFamiliar(familiar);
        }
        logger.debug("Fin guardarFamiliar: resultado={}", resultado);
        return resultado;
    }

    private boolean insertarFamiliar(Familiar familiar) {
        logger.debug("Inicio insertarFamiliar: nombre={}, apellidos={}", familiar.getNombre(), familiar.getApellidos());
        String sql = "INSERT INTO FAMILIARES "
                + "(ID, NOMBRE, APELLIDOS, TELEFONO, EMAIL, RECIBE_INFO_CLUB, WHATSAPP_ACTIVO) "
                + "VALUES (FAMILIARES_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?)";

        boolean insertado = jdbcTemplate.update(
                sql,
                familiar.getNombre(),
                familiar.getApellidos(),
                familiar.getTelefono(),
                familiar.getEmail(),
                familiar.getRecibeInfoClub(),
                familiar.getWhatsappActivo()
        ) == 1;
        logger.info("Familiar insertado: nombre={} {}, resultado={}", familiar.getNombre(), familiar.getApellidos(), insertado);
        logger.debug("Fin insertarFamiliar: insertado={}", insertado);
        return insertado;
    }

    private boolean actualizarFamiliar(Familiar familiar) {
        logger.debug("Inicio actualizarFamiliar: id={}", familiar.getId());
        String sql = "UPDATE FAMILIARES SET "
                + "NOMBRE = ?, "
                + "APELLIDOS = ?, "
                + "TELEFONO = ?, "
                + "EMAIL = ?, "
                + "RECIBE_INFO_CLUB = ?, "
                + "WHATSAPP_ACTIVO = ? "
                + "WHERE ID = ?";

        boolean actualizado = jdbcTemplate.update(
                sql,
                familiar.getNombre(),
                familiar.getApellidos(),
                familiar.getTelefono(),
                familiar.getEmail(),
                familiar.getRecibeInfoClub(),
                familiar.getWhatsappActivo(),
                familiar.getId()
        ) == 1;
        logger.debug("Fin actualizarFamiliar: id={}, actualizado={}", familiar.getId(), actualizado);
        return actualizado;
    }

    @Override
    public Familiar obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        String sql = "SELECT * FROM FAMILIARES WHERE ID = ?";

        try {
            Familiar familiar = jdbcTemplate.queryForObject(sql, FAMILIAR_ROW_MAPPER, id);
            logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
            return familiar;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No se encontro familiar con id={}", id);
            return null;
        }
    }

    @Override
    public List<Familiar> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        String sql = "SELECT * FROM FAMILIARES ORDER BY APELLIDOS, NOMBRE";
        List<Familiar> lista = jdbcTemplate.query(sql, FAMILIAR_ROW_MAPPER);
        logger.debug("Fin obtenerTodos: total={}", lista.size());
        return lista;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        String sql = "DELETE FROM FAMILIARES WHERE ID = ?";
        jdbcTemplate.update(sql, id);
        logger.debug("Fin eliminar: id={}", id);
    }
}