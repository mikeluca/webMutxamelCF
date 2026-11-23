# Guía de importaciones de datos externos

Todas se hacen **subiendo un fichero**, en dos pasos (vista previa y confirmar), desde el panel web (SUPER y ADMIN) o desde
la app (solo `ADMIN_APP`). El servidor nunca descarga nada por su cuenta. Detalle de las rutas de la app en
[api-app-admin-ffcv.md](api-app-admin-ffcv.md).

| Qué | Fichero | De dónde se saca | Panel web | App |
| --- | --- | --- | --- | --- |
| Partidos de la FFCV | `LstPartidos.csv` | intranet de la FFCV | Calendario/Resultados › Importar partidos FFCV | `/api/app/admin/ffcv/partidos/**` |
| Clasificaciones de la FFCV | `ListadoClasificaciones.xlsx` | intranet de la FFCV | `/admin/ffcv/clasificaciones` | `/api/app/admin/ffcv/clasificaciones/**` |
| Clasificación de veteranos (LVCA) | página `.html` guardada | https://ligaveteranosalicante.es/scoretable/ con **Guardar como… → Página web** | bloque "Clasificación de veteranos (LVCA)" de `/admin/ffcv/clasificaciones` | `/api/app/admin/ffcv/veteranos/**` |

## Clasificación de veteranos (LVCA)

- Solo se importan las tablas en las que aparece el Mutxamel (hoy, la Liga y la "Copa grupo C"); van al equipo `Veteranos`,
  una clasificación por competición. Al confirmar se sustituye solo la competición importada.
- La web no da la jornada: se guarda el máximo de PJ de la tabla.
- Los rivales sin club en "Escudos de rivales" se crean sin escudo; hay que subirlo después. Los que ya existen (por nombre
  o alias) se reutilizan.
- Si el equipo `Veteranos` no existe, la importación da un error claro: créalo antes en Equipos.
- No hace falta SQL: un equipo ya podía tener varias clasificaciones (la tabla no restringe por equipo).
- La clasificación pública del equipo trae todas sus competiciones (`competiciones`); la principal sigue en los campos de
  siempre.
