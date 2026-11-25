package com.mikedev.mutxamelcf.dao.impl;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

/**
 * Un JdbcTemplate que falla si una consulta recibe un argumento null "desnudo" (sin SqlParameterValue con su tipo).
 * H2 acepta ese null, pero con Oracle Spring llama a setNull con un tipo desconocido y el driver lo rechaza
 * (ORA-17004: Invalid column type: 268435455), así que los tests con H2 no lo detectarían.
 */
class JdbcTemplateEstricto extends JdbcTemplate {

	JdbcTemplateEstricto(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] == null) {
					throw new AssertionError("El argumento " + (i + 1)
							+ " es un null sin tipo: usa SqlTipado (SqlParameterValue) para que Oracle lo acepte");
				}
			}
		}

		return super.newArgPreparedStatementSetter(args);
	}
}
