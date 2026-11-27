/*
 * Lo que comparten los filtros de los listados del panel (Jugadores y Familiares): el desplegable de Equipo acotado por la
 * categoría, los filtros en la URL y el texto del contador. Cada página pone lo suyo (qué filas, qué cuenta).
 */
const FiltrosPanel = (() => {

	/**
	 * El desplegable de Equipo solo ofrece los equipos de la categoría elegida; si el equipo elegido no es de esa
	 * categoría, vuelve a "Todos". Las opciones llevan data-categoria.
	 */
	function crearSelectorDeEquipos(selectEquipo, selectCategoria) {
		const opciones = Array.from(selectEquipo.options).filter(o => o.value !== '');
		return {
			sincronizar() {
				const categoria = selectCategoria.value;
				const elegido = selectEquipo.value;
				Array.from(selectEquipo.options).forEach(o => { if (o.value !== '') o.remove(); });
				opciones.filter(o => !categoria || o.dataset.categoria === categoria)
					.forEach(o => selectEquipo.appendChild(o));
				selectEquipo.value = Array.from(selectEquipo.options).some(o => o.value === elegido) ? elegido : '';
			}
		};
	}

	/** "12 jugadores en Alevín B", "1 familiar de jugadores de Alevín B", con "(mostrando N)" si la lista es menor. */
	function textoContador(total, uno, varios, conector, nombre, mostrando) {
		let texto = (total === 1 ? '1 ' + uno : total + ' ' + varios) + ' ' + conector + ' ' + nombre;
		if (mostrando !== undefined && mostrando !== total) texto += ' (mostrando ' + mostrando + ')';
		return texto;
	}

	/** La URL del listado con sus filtros; los vacíos no se escriben. */
	function url(base, filtros) {
		const params = new URLSearchParams();
		Object.entries(filtros).forEach(([clave, valor]) => { if (valor) params.set(clave, valor); });
		const consulta = params.toString();
		return base + (consulta ? '?' + consulta : '');
	}

	/** Pone el valor en el desplegable solo si es una de sus opciones. */
	function elegirSiExiste(select, valor) {
		if (valor && Array.from(select.options).some(o => o.value === valor)) select.value = valor;
	}

	return { crearSelectorDeEquipos, textoContador, url, elegirSiExiste };
})();
