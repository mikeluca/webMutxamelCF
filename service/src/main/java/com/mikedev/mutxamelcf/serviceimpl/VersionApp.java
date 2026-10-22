package com.mikedev.mutxamelcf.serviceimpl;

/** Comparación de versiones semánticas de la app ("1.0.10" es posterior a "1.0.9"). */
final class VersionApp {

	private VersionApp() {
	}

	/**
	 * Negativo si [a] es anterior a [b], 0 si son iguales y positivo si es posterior. Ignora el
	 * número de compilación ("1.0.2+6") y el sufijo de pre-lanzamiento; una parte que falte o no
	 * sea un número cuenta como 0.
	 */
	static int comparar(String a, String b) {

		int[] partesA = partes(a);
		int[] partesB = partes(b);

		for (int i = 0; i < Math.max(partesA.length, partesB.length); i++) {

			int x = i < partesA.length ? partesA[i] : 0;
			int y = i < partesB.length ? partesB[i] : 0;

			if (x != y) {
				return Integer.compare(x, y);
			}
		}

		return 0;
	}

	private static int[] partes(String version) {

		String base = version == null ? "" : version.trim().split("[+-]")[0];
		String[] textos = base.split("\\.");
		int[] partes = new int[textos.length];

		for (int i = 0; i < textos.length; i++) {
			try {
				partes[i] = Integer.parseInt(textos[i].trim());
			} catch (NumberFormatException e) {
				partes[i] = 0;
			}
		}

		return partes;
	}
}
