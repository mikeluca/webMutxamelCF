package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.sql.Types;

import org.springframework.jdbc.core.SqlParameterValue;

/**
 * Argumentos de JDBC con su tipo. Un argumento que puede ser null y va en una expresión donde Oracle no deduce su tipo
 * (COALESCE(?, COLUMNA), "? IS NULL"...) no puede pasarse desnudo: Spring llama a setNull con un tipo desconocido y el
 * driver de Oracle lo rechaza (ORA-17004: Invalid column type: 268435455). H2 sí lo acepta, por eso los tests no lo
 * ven.
 */
final class SqlTipado {

	private SqlTipado() {
	}

	static SqlParameterValue texto(String valor) {
		return new SqlParameterValue(Types.VARCHAR, valor);
	}

	static SqlParameterValue numero(Number valor) {
		return new SqlParameterValue(Types.NUMERIC, valor);
	}

	static SqlParameterValue fecha(Timestamp valor) {
		return new SqlParameterValue(Types.TIMESTAMP, valor);
	}
}
