# Ensayo del cierre de temporada en DEV y plan del día real

Cumple la decisión 29 de `transicion-temporada.md`: el cierre es obligatorio ensayarlo en DEV con una copia de PRO y con copia de seguridad previa. Los endpoints de la app están en `api-app-transicion.md`.

> **Los comandos de este documento son plantillas.** No se han ejecutado: ajusta nombres de contenedor, rutas, contraseñas y versión de Oracle a tu servidor antes de copiarlos. Donde pone `<...>` hay un dato tuyo.

## 0. Reglas de oro

1. **Nada de este ensayo se ejecuta contra PRO** salvo la lectura necesaria para exportar (sección 1). Todo lo demás (scripts, arrancar el backend, cerrar, repartir) va contra la copia en `oracle-dev`.
2. **Nunca arranques el backend de PRO con `app.push.habilitado=false` ni con `app.cierre-temporada.permitir-antes-de-inicio=true`.** El primero deja a PRO sin notificaciones sin que nadie se entere; el segundo permite cerrar la temporada antes de su fecha. PRO usa el perfil `prod`, que los trae con `true` y `false` respectivamente (`application-prod.properties`). No los cambies ahí.
3. **El backend contra la copia también puede escribir al exterior** y tiene que quedar neutralizado antes de arrancarlo (sección 2): push (FCM) y **email** (SMTP real: invitaciones, activación, recuperación de contraseña y el aviso de cuentas sin jugadores).
4. Antes de cualquier script o cierre en DEV, haz un volcado de la copia "limpia" (sección 1.5) para poder repetir el ensayo las veces que haga falta.

## 1. Copiar PRO a DEV y copia de seguridad de PRO

Situación: dos contenedores Docker en el mismo servidor, `oracle` (PRO) y `oracle-dev` (DEV). Los datos de la aplicación están en el esquema `SYSTEM`; la aplicación entra como `USER_WEB`, que ve las tablas por **sinónimos** y tiene `GRANT` sobre ellas. Por eso el volcado se limita a los objetos de la aplicación (no se exporta `SYSTEM` entero: contiene objetos internos de Oracle).

La misma exportación sirve para las dos cosas: la copia de seguridad de PRO y la semilla de DEV. Una copia de seguridad que no se ha probado a restaurar no es una copia: la importación en DEV es esa prueba.

### 1.1 Qué objetos exportar

Los objetos de la aplicación son los que tienen sinónimo en `USER_WEB`. Desde `oracle` (PRO), como `SYSTEM`, saca la lista:

```sql
-- Tablas
SELECT s.table_name
FROM   all_synonyms s
JOIN   all_objects o ON o.owner = s.table_owner AND o.object_name = s.table_name
WHERE  s.owner = 'USER_WEB' AND s.table_owner = 'SYSTEM' AND o.object_type = 'TABLE'
ORDER  BY 1;

-- Secuencias (p. ej. SEQ_COMUNICACIONES)
SELECT s.table_name
FROM   all_synonyms s
JOIN   all_objects o ON o.owner = s.table_owner AND o.object_name = s.table_name
WHERE  s.owner = 'USER_WEB' AND s.table_owner = 'SYSTEM' AND o.object_type = 'SEQUENCE'
ORDER  BY 1;
```

Comprobación: las dos listas juntas deben cubrir todo lo que ves en `SELECT table_name FROM user_tables` de `SYSTEM` que sea de la aplicación (`JUGADORES`, `EQUIPO`, `TEMPORADAS`, `USUARIOS_APP`, `PARTIDOS`, `COMUNICACIONES`, `CUOTAS_JUGADOR`…). Si falta una tabla de la aplicación porque no tiene sinónimo, añádela a mano.

Con las listas, crea el fichero de parámetros `exp_pro.par` (una línea `INCLUDE` por tipo; Oracle arrastra solo los índices, restricciones y grants de las tablas incluidas):

```
DIRECTORY=DUMP_DIR
DUMPFILE=pro_AAAAMMDD.dmp
LOGFILE=exp_pro_AAAAMMDD.log
SCHEMAS=SYSTEM
FLASHBACK_TIME=SYSTIMESTAMP
INCLUDE=TABLE:"IN ('JUGADORES','EQUIPO','TEMPORADAS', ... todas las tablas de la lista ...)"
INCLUDE=SEQUENCE:"IN ('SEQ_COMUNICACIONES', ... )"
INCLUDE=OBJECT_GRANT
```

`FLASHBACK_TIME` da una exportación consistente aunque PRO siga en uso (no hace falta parar nada).

### 1.2 Exportar PRO (sin parar PRO)

```bash
# Una sola vez, en PRO, como SYSTEM: carpeta de volcados
docker exec -i oracle sqlplus -s system/<PWD_PRO>@<SERVICIO> <<'SQL'
CREATE OR REPLACE DIRECTORY DUMP_DIR AS '/opt/oracle/dumps';
SQL
docker exec oracle mkdir -p /opt/oracle/dumps

# Exportación
docker cp exp_pro.par oracle:/opt/oracle/dumps/exp_pro.par
docker exec oracle expdp system/<PWD_PRO>@<SERVICIO> parfile=/opt/oracle/dumps/exp_pro.par
```

Mira el final de `exp_pro_AAAAMMDD.log`: tiene que decir `successfully completed` y listar todas las tablas con su número de filas.

### 1.3 Guardar la copia de seguridad de PRO

```bash
mkdir -p /srv/backups/mutxamel/AAAAMMDD
docker cp oracle:/opt/oracle/dumps/pro_AAAAMMDD.dmp       /srv/backups/mutxamel/AAAAMMDD/
docker cp oracle:/opt/oracle/dumps/exp_pro_AAAAMMDD.log   /srv/backups/mutxamel/AAAAMMDD/
sha256sum /srv/backups/mutxamel/AAAAMMDD/pro_AAAAMMDD.dmp > /srv/backups/mutxamel/AAAAMMDD/pro_AAAAMMDD.sha256
```

Guarda además **una copia fuera del servidor** (otro equipo o la nube del club). Apunta junto al volcado: la fecha, el commit desplegado en PRO y qué scripts de `sql/` tenía aplicados PRO en ese momento. Los volcados contienen datos personales de menores y de familias: no los subas a un repositorio ni a un servicio sin cifrar.

Alternativa más rápida pero **con parada de PRO**: copiar en frío el volumen de datos de Oracle (`docker stop oracle`, copiar el volumen, `docker start oracle`). Solo si hay una ventana en la que PRO pueda estar parado unos minutos; Data Pump no necesita ventana.

### 1.4 Importar en DEV

Primero, la comprobación de que **el contenedor es el de desarrollo**:

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'      # oracle-dev, y el puerto que NO usa PRO
```

Limpia de `oracle-dev` los objetos de la aplicación (así la importación crea todo de cero, con la estructura de PRO, y los scripts de `sql/` se aplican después igual que en PRO). Genera las sentencias y revísalas antes de ejecutarlas:

```sql
-- En oracle-dev, como SYSTEM. Mismas listas de la sección 1.1.
SELECT 'DROP TABLE SYSTEM.' || table_name || ' CASCADE CONSTRAINTS PURGE;'
FROM   all_synonyms s JOIN all_objects o ON o.owner = s.table_owner AND o.object_name = s.table_name
WHERE  s.owner = 'USER_WEB' AND s.table_owner = 'SYSTEM' AND o.object_type = 'TABLE';

SELECT 'DROP SEQUENCE SYSTEM.' || table_name || ';'
FROM   all_synonyms s JOIN all_objects o ON o.owner = s.table_owner AND o.object_name = s.table_name
WHERE  s.owner = 'USER_WEB' AND s.table_owner = 'SYSTEM' AND o.object_type = 'SEQUENCE';
```

Si DEV no tiene aún los sinónimos de algunas tablas (porque en PRO existen y en DEV no), la lista de DEV será más corta: no importa, el `DROP` de lo que exista y la importación hacen el resto. Luego:

```bash
docker exec -i oracle-dev sqlplus -s system/<PWD_DEV>@<SERVICIO_DEV> <<'SQL'
CREATE OR REPLACE DIRECTORY DUMP_DIR AS '/opt/oracle/dumps';
SQL
docker exec oracle-dev mkdir -p /opt/oracle/dumps
docker cp /srv/backups/mutxamel/AAAAMMDD/pro_AAAAMMDD.dmp oracle-dev:/opt/oracle/dumps/

docker exec oracle-dev impdp system/<PWD_DEV>@<SERVICIO_DEV> \
  directory=DUMP_DIR dumpfile=pro_AAAAMMDD.dmp logfile=imp_dev_AAAAMMDD.log \
  schemas=SYSTEM table_exists_action=REPLACE
```

Después de la importación:

1. **Sinónimos y permisos de `USER_WEB`**: el volcado lleva los `GRANT`, pero comprueba que no falte ninguno. Cada tabla de la aplicación debe tener sinónimo y los cuatro permisos:
   ```sql
   SELECT t.table_name
   FROM   all_objects t
   WHERE  t.owner = 'SYSTEM' AND t.object_type IN ('TABLE','SEQUENCE')
     AND  t.object_name IN (<lista de la sección 1.1>)
     AND  NOT EXISTS (SELECT 1 FROM all_synonyms s WHERE s.owner = 'USER_WEB' AND s.table_name = t.object_name);
   -- Debe devolver 0 filas. Si no, crea el sinónimo y el GRANT como en los scripts de sql/:
   --   GRANT SELECT, INSERT, UPDATE, DELETE ON SYSTEM.<TABLA> TO USER_WEB;
   --   CREATE OR REPLACE SYNONYM USER_WEB.<TABLA> FOR SYSTEM.<TABLA>;
   ```
2. **Contraseña de `USER_WEB` en DEV**: no cambia (el usuario no se importa).
3. **Compara los recuentos** de las tablas más importantes entre PRO y DEV (`JUGADORES`, `JUGADOR_TEMPORADA`, `PARTIDOS`, `USUARIOS_APP`, `CUOTAS_JUGADOR`, `COMUNICACIONES`): deben coincidir con el log de la exportación.

### 1.5 Volcado de la copia "limpia" de DEV

Cuando DEV tenga la copia de PRO y los scripts aplicados (sección 3), exporta DEV con el mismo procedimiento a `dev_listo_AAAAMMDD.dmp`. Es el punto de vuelta atrás del ensayo (sección 8). Hazlo **antes** de anonimizar o de empezar el ensayo si quieres un punto sin cambios, pero lo normal es dejarlo **después de la sección 2** (ya anonimizado y con los scripts aplicados), para no repetir esos pasos.

## 2. Seguridad antes de arrancar el backend contra la copia

Hazlo **antes** del primer arranque, con la copia ya importada. Ordenado de más a menos importante:

### 2.1 Email: anonimizar las cuentas de la copia

El perfil `local` tiene `spring.mail.*` configurado con las variables `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`. Si apuntan al servidor real, el backend de DEV **enviará correos a las familias de verdad** en estos casos: invitaciones, reenvío de activación, recuperación de contraseña, cuenta activada y el aviso de "tu cuenta se desactivará" del job diario de las 04:00 (cuentas sin jugadores activos). Dos defensas, mejor las dos:

1. Cambiar los emails de la copia para que no lleguen a nadie, dejando solo las cuentas con las que vas a probar (pon aquí las tuyas):
   ```sql
   -- En oracle-dev. <MIS_CUENTAS>: tu cuenta de admin/coordinador y las de prueba.
   UPDATE USUARIOS_APP
      SET EMAIL = 'dev' || ID || '@ejemplo.invalid'
    WHERE LOWER(EMAIL) NOT IN (<MIS_CUENTAS en minúsculas>);
   COMMIT;
   ```
   (Si `EMAIL` es `UNIQUE`, el patrón con el `ID` no choca.) Los emails de `FAMILIARES` y teléfonos del panel no los usa ningún envío automático de la app, pero si el panel de DEV tiene un botón de "Invitar", usa solo cuentas tuyas.
2. Apuntar `MAIL_HOST` a algo inofensivo para el arranque de DEV (por ejemplo un servidor de captura como MailHog en `localhost:1025`, o un host que no existe). Un envío fallido solo se registra en el log; no rompe nada.

Los correos que van a la bandeja del club (`mutxamelcf.gestion@gmail.com`: recordatorio de importar partidos FFCV, reportes) también salen del perfil `local`: es el club, no familias, pero no los dejes funcionando de forma desatendida.

### 2.2 Push: `app.push.habilitado=false`

En `mvc/src/main/resources/application-local.properties` (ya viene así en el repositorio, comprueba que tu copia local no lo haya cambiado):

```properties
app.push.habilitado=false
app.cierre-temporada.permitir-antes-de-inicio=true
```

Con `app.push.habilitado=false`, `FcmPushServiceImpl` no envía nada a Firebase y solo registra `push desactivado` con el tipo, el destinatario y el título. Las notificaciones de la bandeja de la app **se siguen creando** (es lo que quieres ver en el ensayo).
`app.cierre-temporada.permitir-antes-de-inicio=true` es lo que deja cerrar antes del 1 de julio (en `application.properties` y en `prod` vale `false`, y el cierre se bloquea con "no se puede cerrar antes").

Cómo comprobarlo al arrancar (en el log, tras el arranque con el perfil `local`): busca `push desactivado` la primera vez que se envíe algo, y que el perfil activo sea `local` (`The following 1 profile is active: "local"`).

### 2.3 Opcional: borrar los tokens FCM de la copia

Aunque `app.push.habilitado=false` ya impide enviar, quitar los tokens de la copia es un segundo cierre por si alguien arranca DEV con la propiedad mal puesta:

```sql
-- En oracle-dev
DELETE FROM USUARIOS_APP_DISPOSITIVOS;
COMMIT;
-- o, más suave, solo invalidarlos:
-- UPDATE USUARIOS_APP_DISPOSITIVOS SET TOKEN_FCM = 'borrado-' || ID;
```

Si lo borras, para probar el push real con tu móvil tendrás que volver a entrar en la app (se registra el token de nuevo). Con `app.push.habilitado=false` no lo necesitas para el ensayo.

### 2.4 Que el backend apunte a DEV (la comprobación que no se salta)

Antes de arrancar:

- `DB_URL` (variable de entorno del perfil `local`) debe apuntar al puerto/servicio de **`oracle-dev`**. Escríbelo en una nota al lado del terminal con el puerto de cada entorno.
- Ten **un solo backend** arrancado a la vez en tu equipo, y no el de PRO.
- Un `SELECT` de sentido común nada más conectar: `SELECT COUNT(*) FROM USUARIOS_APP WHERE EMAIL LIKE 'dev%@ejemplo.invalid'` debe devolver casi todas las cuentas (si devuelve 0, **no** estás en la copia anonimizada).
- No dejes el backend de DEV encendido toda la noche: a las 03:30 corre la purga de comunicaciones y a las 04:00 el job de cuentas sin jugadores, y a las 09:00 del 1 de mayo (si pruebas en esa fecha) el de continuidad. Para probarlos, lánzalos tú a propósito.

## 3. Scripts SQL que tienen que estar aplicados, en orden

Se ejecutan con el usuario **propietario del esquema** (`SYSTEM`), no con `USER_WEB`, y **no se pueden repetir** (la segunda vez fallan con ORA-01430, ORA-00955…). Cada script lleva en la cabecera qué comprobar. Aplícalos en DEV en este orden sobre la copia de PRO; el ensayo también sirve de prueba general de los scripts.

| # | Script | Qué hace | Cuándo en PRO |
|---|---|---|---|
| 1 | `2026-10-29_plantillas_temporada.sql` | `JUGADOR_TEMPORADA`, `CATEGORIAS_EDAD` y `JUGADORES.EQUIPO_ID`, `ANIO_NACIMIENTO`, `FECHA_BAJA` | **Ya aplicado en DEV y PRO** (la copia de PRO ya lo trae; no ejecutar) |
| 1b | `2026-10-30_cuerpo_tecnico_equipo_id.sql` | Tabla `CUERPO_TECNICO_EQUIPO` (cuerpo técnico en varios equipos, N:M) | **Ya aplicado en DEV y PRO** (la copia de PRO ya lo trae; no ejecutar) |
| 2 | `2026-10-29_modelo_transicion_temporada.sql` — **parte A** | Columnas nuevas de `JUGADOR_TEMPORADA`, `EQUIPO_TEMPORADA`, `CUERPO_TECNICO_TEMPORADA`, `TEMPORADA_ID` opcional en `CLASIFICACIONES`, `EQUIPOS_FFCV`, `HORARIOS_ENTRENAMIENTO`, estado `INCOBRABLE` | Antes de desplegar (no rompe el código anterior) |
| 3 | `2026-10-29_modelo_transicion_temporada.sql` — **parte B** | Rellena los datos con la temporada activa y pone `TEMPORADA_ID` obligatorio | **Justo antes** de desplegar el código nuevo |
| 4 | `2026-10-30_jugador_temporada_sin_baja.sql` | Quita `JUGADOR_TEMPORADA.BAJA` (la baja es `ESTADO = 'BAJA'`) | **Justo antes** de desplegar; ventana corta (el código anterior falla con ORA-00904) |
| 5 | `2026-10-31_pregunta_continuidad.sql` | `JUGADOR_TEMPORADA.FECHA_PREGUNTA_CONTINUIDAD` | Antes de desplegar |
| 6 | `2026-11-01_cuentas_sin_jugadores.sql` | `USUARIOS_APP.FECHA_AVISO_SIN_JUGADORES` y `DESACTIVADA_SIN_JUGADORES` | Antes de desplegar |

**Ya aplicados en PRO:** `2026-10-29_plantillas_temporada.sql` (#1) y `2026-10-30_cuerpo_tecnico_equipo_id.sql` (#1b) están en `sql/` marcados como «NO EJECUTAR». Como la copia de PRO ya los trae, **en el ensayo en DEV no se ejecutan**: solo se comprueban con las consultas de abajo.

Los scripts anteriores a `2026-10-29` (hasta `2026-10-27_mejoras_roles.sql`) se dan por aplicados en PRO.

Comprobación de qué hay aplicado (en PRO es solo lectura; hazla antes del ensayo y la vez que cierres):

```sql
-- #1
SELECT table_name FROM user_tables WHERE table_name IN ('JUGADOR_TEMPORADA','CATEGORIAS_EDAD');            -- 2 filas
SELECT column_name FROM user_tab_columns WHERE table_name = 'JUGADORES'
  AND column_name IN ('EQUIPO_ID','ANIO_NACIMIENTO','FECHA_BAJA');                                           -- 3 filas
-- #1b
SELECT table_name FROM user_tables WHERE table_name = 'CUERPO_TECNICO_EQUIPO';                              -- 1 fila
SELECT COUNT(*) FROM CUERPO_TECNICO_EQUIPO;                                                                  -- > 0
-- #2 y #3
SELECT table_name FROM user_tables WHERE table_name IN ('EQUIPO_TEMPORADA','CUERPO_TECNICO_TEMPORADA');     -- 2 filas
SELECT column_name FROM user_tab_columns WHERE table_name = 'JUGADOR_TEMPORADA'
  AND column_name IN ('ESTADO','CONTINUIDAD','FECHA_CONTINUIDAD','USUARIO_APP_CONTINUIDAD_ID','EQUIPO_DEFINITIVO'); -- 5 filas
SELECT table_name, nullable FROM user_tab_columns WHERE column_name = 'TEMPORADA_ID'
  AND table_name IN ('CLASIFICACIONES','EQUIPOS_FFCV','HORARIOS_ENTRENAMIENTO');                           -- 3 filas, nullable = N tras la parte B
-- #4: no debe existir
SELECT column_name FROM user_tab_columns WHERE table_name = 'JUGADOR_TEMPORADA' AND column_name = 'BAJA';   -- 0 filas
-- #5
SELECT column_name FROM user_tab_columns WHERE table_name = 'JUGADOR_TEMPORADA' AND column_name = 'FECHA_PREGUNTA_CONTINUIDAD'; -- 1 fila
-- #6
SELECT column_name FROM user_tab_columns WHERE table_name = 'USUARIOS_APP'
  AND column_name IN ('FECHA_AVISO_SIN_JUGADORES','DESACTIVADA_SIN_JUGADORES');                             -- 2 filas
```

Orden de despliegue (cuando llegue a PRO): scripts #2, #5, #6 → **justo antes de desplegar** #3 y #4 → desplegar el backend → publicar la app. Nunca la app antes que el backend.

## 4. Checklist del ensayo completo

Perfil: backend contra `oracle-dev`, perfil `local`, secciones 2 y 3 hechas. Necesitas: una cuenta SUPER del panel, una cuenta COORDINADOR o ADMIN_APP de la app, y una cuenta de familia de prueba con 3 jugadores vinculados (puedes enlazar a mano una familia de la copia a tu email). Para llamar a la API: `POST /api/app/auth/login` con `{ "email": ..., "password": ... }` devuelve el `token`; úsalo como `Authorization: Bearer <token>`. La colección de Postman `MutxamelCF-API.postman_collection.json` ya trae las peticiones.

Cada paso lleva "qué mirar" y, entre corchetes, la sección de verificación SQL (6) que hay que lanzar antes y después.

**Fase 0: línea base**
- [ ] Haz las consultas de la sección 6 (**antes**) y guarda la salida en un fichero `antes.txt`.
- [ ] Panel → Pagos → Temporadas: hay una temporada activa (`2026/2027`) y las fechas de las temporadas son contiguas.
- [ ] App y web pública funcionan con los datos de la copia (partidos, clasificaciones, plantillas).

**Fase 1: crear la temporada siguiente y el borrador (abril-mayo)**
- [ ] Pagos → Temporadas: crea `2027/2028` (1/7/2027 → 30/6/2028), **sin** marcarla como activa. Comprueba que no se solapa.
- [ ] Panel → `Nueva temporada` (`/admin/nueva-temporada`): revisa `CATEGORIAS_EDAD`.
- [ ] Rellena los años de nacimiento que falten [6.4: jugadores activos sin año]. Debe quedar en 0.
- [ ] Genera el borrador (`Generar`). Mira: cada jugador formativo con su categoría por edad; Filial, Primer Equipo y Veteranos se quedan en su equipo; el Juvenil de último año queda pendiente.
- [ ] `Regenerar` (con cuidado): comprueba si conserva las decisiones ya tomadas antes de usarlo en PRO; anota lo que ocurre.

**Fase 2: pedir confirmación a las familias**
- [ ] Botón "Pedir confirmación a las familias" (solo SUPER). Resultado: jugadores preguntados y cuentas avisadas.
- [ ] Repite el botón: **no** vuelve a avisar a nadie (idempotente) [`FECHA_PREGUNTA_CONTINUIDAD` ya rellena].
- [ ] En el log aparece `push desactivado` con `tipo=CONTINUIDAD`; en la app de la familia de prueba la bandeja tiene la notificación y la pantalla "Continuidad" lista a los jugadores.
- [ ] Lunes (lanzarlo a mano o esperar): recordatorio solo a quien sigue en PENDIENTE.

**Fase 3: respuestas desde la API** (con el token de la familia de prueba)
- [ ] `GET /api/app/continuidad`: tres jugadores, `respuesta = PENDIENTE`.
- [ ] `PUT /api/app/continuidad/{jugador1}` con `SIGUE` → 200.
- [ ] `PUT /api/app/continuidad/{jugador2}` con `NO_SIGUE` → 200; los coordinadores reciben `CONTINUIDAD_RESPUESTA`.
- [ ] `PUT /api/app/continuidad/{jugador3}` con `DUDA` → 200; aviso a coordinadores.
- [ ] Cambia una respuesta (`NO_SIGUE` → `SIGUE`): se deshace la baja propuesta y el aviso solo sale cuando la respuesta nueva es `NO_SIGUE` o `DUDA`.
- [ ] Errores: respuesta inválida → 400; jugador ajeno → 403.
- [ ] Panel: la columna de continuidad refleja las tres respuestas.

**Fase 4: altas de prueba**
- [ ] `POST /api/app/pruebas` con `{ "nombre", "apellidos", "anioNacimiento" }` (token de coordinador) → 201 `estado: PRUEBA` y su categoría por edad.
- [ ] Alta de prueba desde el panel (`/prueba/anadir`).
- [ ] La prueba **no** aparece en listados de jugadores activos ni en bajas, y no tiene que contestar la continuidad.

**Fase 5: resolver pendientes**
- [ ] `NO_SIGUE` → confirmar baja; `DUDA` y sin respuesta → decidir; Juvenil de último año → Filial, Primer Equipo o baja; jugadores "Sin propuesta" → añadirlos.
- [ ] El resumen del cierre (`Cerrar temporada`) lista los **bloqueos** hasta que no quede ninguno: jugadores sin fila, filas pendientes, jugadores sin año de nacimiento y (en PRO) cierre antes de la fecha.
- [ ] Anota el resumen que muestra (bajas, pruebas, formativos, adultos, horarios, sesiones, conceptos, notificaciones): lo compararás con lo que pase.

**Fase 6: cerrar la temporada**
- [ ] Haz las consultas de la sección 6 (**antes del cierre**), guarda `antes_cierre.txt` y el volcado `dev_antes_cierre_AAAAMMDD.dmp`.
- [ ] `Cerrar temporada` (solo SUPER). Pide escribir el nombre exacto de la temporada. Pruébalo con un nombre incorrecto: debe rechazarse sin cambiar nada.
- [ ] Cierre con el nombre correcto: "Temporada cerrada y activada la nueva".
- [ ] Haz las consultas de la sección 6 (**después**), guarda `despues_cierre.txt` y compara.
- [ ] Log: aviso "Nueva temporada" enviado a todos (paso 9, fuera de la transacción).

**Fase 7: comprobar la pretemporada**
- [ ] Un equipo "`<Categoría> (Pretemporada)`" por categoría formativa con jugadores, `TIPO = PRETEMPORADA`, activo.
- [ ] Los jugadores formativos (y las pruebas) están en él con `EQUIPO_DEFINITIVO = 0`; los adultos se quedan en su equipo.
- [ ] Dorsales a 0, salvo Filial, Primer Equipo y Veteranos.
- [ ] Cuerpo técnico desasignado salvo Primer Equipo, Filial y Veteranos; `CUERPO_TECNICO_TEMPORADA` tiene la foto de la temporada cerrada.
- [ ] Horarios de la temporada anterior desactivados; copiados (desactivados) a la nueva; sesiones futuras borradas.
- [ ] Conceptos de pago copiados a la temporada nueva.
- [ ] Notificaciones anteriores borradas (solo queda el aviso de cambio de temporada).
- [ ] Un entrenador sin equipo entra en la app, ve su perfil y las comunicaciones generales.
- [ ] El coordinador asigna entrenadores a los equipos de pretemporada y pasa lista / crea amistosos.

**Fase 8: reparto en septiembre** (panel `/admin/reparto` y app, "Reparto de equipos")
- [ ] `GET /api/app/reparto/categorias` y `GET /api/app/reparto?categoria=Alevín`: pretemporada, por asignar y equipos.
- [ ] `POST /api/app/reparto/asignar` con un equipo definitivo y varios jugadores (con dorsal y sin él). Con una prueba sin confirmar: 409 `PRUEBAS_SIN_CONFIRMAR`; repetir con `confirmarPruebas: true`.
- [ ] Se generan las cuotas del jugador **solo en la primera asignación definitiva** y nunca a las pruebas ni en pretemporada [6.9]. Mover entre dos equipos definitivos no crea cuotas.
- [ ] Dorsal repetido: avisa, no impide.
- [ ] El equipo definitivo con su primer jugador pasa a activo; la pretemporada vacía se desactiva.
- [ ] `POST /api/app/reparto/devolver` de un jugador asignado por error: vuelve a la pretemporada sin dorsal; sus cuotas sin pagos se borran y las que tienen pagos se conservan con aviso.
- [ ] `POST /api/app/reparto/equipos?categoria=Alevín`: crea el siguiente equipo (nace inactivo).
- [ ] Mismas operaciones desde el panel. Permisos: reparto e historial para SUPER y ADMIN.

**Fase 9: cuotas y morosidad**
- [ ] Cuotas de los conceptos activos de la temporada: las mensuales de septiembre a junio si se asigna en septiembre o antes; desde el mes de la asignación si es posterior.
- [ ] `GET /api/app/cuotas` de una familia: **solo** cuotas de la temporada activa.
- [ ] Panel `/admin/pagos/morosidad` (solo SUPER): cuotas sin pagar de la temporada anterior, marcar cobrada, marcar/deshacer incobrable, exportar CSV.
- [ ] Registra un pago y comprueba el estado (PENDIENTE → PARCIAL → PAGADO).

**Fase 10: tareas periódicas (lánzalas a propósito)**
- [ ] Comunicaciones: un aviso de equipo anterior al inicio de la nueva temporada **ya no se ve** en la app; los chats privados sí.
- [ ] Purga: con la copia de PRO no habrá comunicaciones de hace más de dos temporadas; si quieres verla actuar, prepara datos de prueba en una copia aparte. El log de la purga dice cuántas filas borra de cada tabla.
- [ ] Cuentas sin jugadores: tras el cierre, las familias con todos sus jugadores dados de baja deben recibir el aviso (push + email; con el email anonimizado no llega a nadie) y quedar con fecha de aviso; las que tienen una prueba oculta **no**.

## 5. Qué revisar en la app y en la web pública

### Después de generar el borrador y pedir confirmación (temporada activa todavía la vieja)
- **Web pública y app: no cambia nada.** La temporada activa sigue siendo la vieja.
- App familia: tarjeta de continuidad en "Mis jugadores" con Sí / No / Aún no lo sé.
- App coordinación: aviso de `CONTINUIDAD_RESPUESTA`.

### Justo después del cierre (modo "verano")
La temporada nueva está activa y **todavía no tiene partidos**; la web pública entra en modo verano:
- Plantillas: "**Plantillas en preparación**" en vez de las plantillas.
- Resultados y clasificaciones: **los de la temporada anterior**, hasta que empiece la competición (cuando la nueva temporada tenga partidos, vuelve a lo normal).
- Los equipos de pretemporada **no** salen en la web pública.
- Web: los equipos que no juegan la nueva temporada no aparecen.
- App: la bandeja tiene el aviso "Nueva temporada"; las familias ven a sus jugadores con la categoría nueva y "`<Categoría> · Pretemporada`"; no hay horarios ni sesiones de la temporada vieja en el futuro (los horarios se copian desactivados: el calendario de la pretemporada se llena cuando el coordinador crea o activa los suyos).
- Panel: Historial (`/admin/historial`) muestra la temporada cerrada con sus plantillas y cuerpos técnicos; Pagos → Temporadas con la nueva activa.

### Después del reparto
- Web pública: sigue en modo verano mientras no haya partidos de la nueva temporada; los equipos definitivos con jugadores aparecen en cuanto se activan.
- App: el jugador pasa de "Pretemporada" a su equipo definitivo; ve las cuotas de la nueva temporada.
- Cuando se importen los primeros partidos de la nueva temporada (FFCV), la web **sale del modo verano** y enseña la temporada actual: compruébalo importando un partido de prueba de la temporada nueva y borrándolo después, solo en DEV.

## 6. Consultas de verificación (antes y después)

Lánzalas en `oracle-dev`, como `SYSTEM`, antes del ensayo, antes del cierre y después del cierre. Guarda la salida de cada vez. Sustituye `<ACTIVA>` y `<NUEVA>` por los `ID` de `TEMPORADAS`.

```sql
-- 6.0 Temporadas
SELECT ID, NOMBRE, FECHA_INICIO, FECHA_FIN, ACTIVA FROM TEMPORADAS ORDER BY FECHA_INICIO;

-- 6.1 Recuento por tabla (visión general)
SELECT 'JUGADORES' T, COUNT(*) N FROM JUGADORES UNION ALL
SELECT 'JUGADORES activos', COUNT(*) FROM JUGADORES WHERE FECHA_BAJA IS NULL UNION ALL
SELECT 'JUGADOR_TEMPORADA', COUNT(*) FROM JUGADOR_TEMPORADA UNION ALL
SELECT 'EQUIPO_TEMPORADA', COUNT(*) FROM EQUIPO_TEMPORADA UNION ALL
SELECT 'CUERPO_TECNICO_EQUIPO', COUNT(*) FROM CUERPO_TECNICO_EQUIPO UNION ALL
SELECT 'CUERPO_TECNICO_TEMPORADA', COUNT(*) FROM CUERPO_TECNICO_TEMPORADA UNION ALL
SELECT 'HORARIOS_ENTRENAMIENTO', COUNT(*) FROM HORARIOS_ENTRENAMIENTO UNION ALL
SELECT 'SESIONES_ENTRENAMIENTO', COUNT(*) FROM SESIONES_ENTRENAMIENTO UNION ALL
SELECT 'CONCEPTOS_PAGO', COUNT(*) FROM CONCEPTOS_PAGO UNION ALL
SELECT 'CUOTAS_JUGADOR', COUNT(*) FROM CUOTAS_JUGADOR UNION ALL
SELECT 'NOTIFICACIONES_APP', COUNT(*) FROM NOTIFICACIONES_APP UNION ALL
SELECT 'COMUNICACIONES', COUNT(*) FROM COMUNICACIONES UNION ALL
SELECT 'USUARIOS_APP activos', COUNT(*) FROM USUARIOS_APP WHERE ACTIVO = 1 AND FECHA_ELIMINACION IS NULL;

-- 6.2 JUGADOR_TEMPORADA por temporada, estado y equipo definitivo
SELECT t.NOMBRE TEMPORADA, jt.ESTADO, jt.EQUIPO_DEFINITIVO, jt.CONTINUIDAD, COUNT(*) N
FROM   JUGADOR_TEMPORADA jt JOIN TEMPORADAS t ON t.ID = jt.TEMPORADA_ID
GROUP  BY t.NOMBRE, jt.ESTADO, jt.EQUIPO_DEFINITIVO, jt.CONTINUIDAD
ORDER  BY 1, 2, 3, 4;

-- 6.3 EQUIPO_TEMPORADA por temporada, tipo y activo
SELECT t.NOMBRE TEMPORADA, et.TIPO, et.ACTIVO, COUNT(*) N
FROM   EQUIPO_TEMPORADA et JOIN TEMPORADAS t ON t.ID = et.TEMPORADA_ID
GROUP  BY t.NOMBRE, et.TIPO, et.ACTIVO ORDER BY 1, 2, 3;

-- 6.4 Jugadores activos sin año de nacimiento (debe ser 0 para poder cerrar)
SELECT ID, NOMBRE, APELLIDOS, CATEGORIA FROM JUGADORES
WHERE  FECHA_BAJA IS NULL AND ANIO_NACIMIENTO IS NULL ORDER BY APELLIDOS;

-- 6.5 Cuerpo técnico: asignaciones actuales, foto por temporada y fichas / cuentas de entrenador sin equipo
SELECT COUNT(*) AS ASIGNACIONES_ACTUALES FROM CUERPO_TECNICO_EQUIPO;
SELECT t.NOMBRE TEMPORADA, COUNT(*) N
FROM   CUERPO_TECNICO_TEMPORADA ctt JOIN TEMPORADAS t ON t.ID = ctt.TEMPORADA_ID GROUP BY t.NOMBRE ORDER BY 1;
SELECT ct.ID, ct.NOMBRE, ct.APELLIDOS FROM CUERPO_TECNICO ct
WHERE  NOT EXISTS (SELECT 1 FROM CUERPO_TECNICO_EQUIPO cte WHERE cte.CUERPO_TECNICO_ID = ct.ID);   -- fichas sin equipo
SELECT u.ID, u.EMAIL FROM USUARIOS_APP u
WHERE  EXISTS (SELECT 1 FROM USUARIOS_APP_ROLES ur JOIN ROLES_APP r ON r.ID = ur.ROL_ID
               WHERE ur.USUARIO_APP_ID = u.ID AND r.CODIGO = 'ENTRENADOR')
  AND  NOT EXISTS (SELECT 1 FROM USUARIOS_APP_CUERPO_TECNICO uct
                   JOIN CUERPO_TECNICO_EQUIPO cte ON cte.CUERPO_TECNICO_ID = uct.CUERPO_TECNICO_ID
                   WHERE uct.USUARIO_APP_ID = u.ID);                                               -- cuentas ENTRENADOR sin equipo

-- 6.6 Horarios por temporada y activo; sesiones futuras
SELECT TEMPORADA_ID, ACTIVO, COUNT(*) N FROM HORARIOS_ENTRENAMIENTO GROUP BY TEMPORADA_ID, ACTIVO ORDER BY 1, 2;
SELECT COUNT(*) AS SESIONES_FUTURAS FROM SESIONES_ENTRENAMIENTO WHERE FECHA > TRUNC(SYSDATE);
SELECT e.NOMBRE, COUNT(*) N FROM SESIONES_ENTRENAMIENTO s JOIN EQUIPO e ON e.ID = s.EQUIPO_ID
WHERE  s.FECHA > TRUNC(SYSDATE) GROUP BY e.NOMBRE ORDER BY 1;

-- 6.7 Conceptos de pago por temporada
SELECT t.NOMBRE TEMPORADA, cp.ACTIVO, COUNT(*) N
FROM   CONCEPTOS_PAGO cp JOIN TEMPORADAS t ON t.ID = cp.TEMPORADA_ID GROUP BY t.NOMBRE, cp.ACTIVO ORDER BY 1, 2;

-- 6.8 Cuotas por temporada y estado
SELECT t.NOMBRE TEMPORADA, c.ESTADO, COUNT(*) N, SUM(c.IMPORTE) IMPORTE
FROM   CUOTAS_JUGADOR c
JOIN   CONCEPTOS_PAGO cp ON cp.ID = c.CONCEPTO_PAGO_ID
JOIN   TEMPORADAS t ON t.ID = cp.TEMPORADA_ID
GROUP  BY t.NOMBRE, c.ESTADO ORDER BY 1, 2;

-- 6.9 Cuotas que no deberían existir: de jugadores PRUEBA o de un equipo de pretemporada
SELECT c.ID, c.JUGADOR_ID FROM CUOTAS_JUGADOR c
JOIN   CONCEPTOS_PAGO cp ON cp.ID = c.CONCEPTO_PAGO_ID
JOIN   JUGADOR_TEMPORADA jt ON jt.JUGADOR_ID = c.JUGADOR_ID AND jt.TEMPORADA_ID = cp.TEMPORADA_ID
WHERE  jt.ESTADO = 'PRUEBA' OR jt.EQUIPO_DEFINITIVO = 0;                                          -- debe ser 0 filas

-- 6.10 Notificaciones y tokens (tras el cierre solo debe quedar el aviso de cambio de temporada)
SELECT TIPO, COUNT(*) N FROM NOTIFICACIONES_APP GROUP BY TIPO ORDER BY 2 DESC;
SELECT COUNT(*) AS DISPOSITIVOS FROM USUARIOS_APP_DISPOSITIVOS;

-- 6.11 Cuentas en el circuito de "sin jugadores activos"
SELECT COUNT(FECHA_AVISO_SIN_JUGADORES) AVISADAS, SUM(DESACTIVADA_SIN_JUGADORES) DESACTIVADAS FROM USUARIOS_APP;
```

**Qué esperar al cerrar:**
- 6.2: la temporada nueva pasa a tener filas CONFIRMADO con `EQUIPO_DEFINITIVO = 0` (formativos y pruebas) y `= 1` (adultos); las `BAJA` son las del borrador.
- 6.3: aparecen las filas de la temporada nueva, con `TIPO = PRETEMPORADA` activas (una por categoría formativa con jugadores).
- 6.5: `CUERPO_TECNICO_EQUIPO` baja a las asignaciones de Primer Equipo, Filial y Veteranos; `CUERPO_TECNICO_TEMPORADA` gana la foto de la temporada cerrada.
- 6.6: los horarios de la temporada cerrada pasan a `ACTIVO = 0` y aparecen los de la nueva (desactivados); `SESIONES_FUTURAS = 0`.
- 6.7: los conceptos de la temporada nueva son una copia de los de la vieja.
- 6.9: 0 filas siempre.
- 6.10: las notificaciones anteriores desaparecen.

## 7. Cómo volver atrás en DEV

La copia de DEV es descartable. Para repetir el ensayo desde un punto conocido:

```bash
# 1. Limpiar los objetos de la aplicación en oracle-dev (sección 1.4: los DROP generados)
# 2. Importar el volcado del punto elegido
docker cp /srv/backups/mutxamel/AAAAMMDD/dev_listo_AAAAMMDD.dmp oracle-dev:/opt/oracle/dumps/
docker exec oracle-dev impdp system/<PWD_DEV>@<SERVICIO_DEV> \
  directory=DUMP_DIR dumpfile=dev_listo_AAAAMMDD.dmp logfile=imp_vuelta_AAAAMMDD.log \
  schemas=SYSTEM table_exists_action=REPLACE
# 3. Comprobar sinónimos y permisos de USER_WEB (sección 1.4) y reiniciar el backend de DEV
```

Puntos de vuelta que conviene tener: `pro_AAAAMMDD.dmp` (PRO sin tocar), `dev_listo_AAAAMMDD.dmp` (con scripts y anonimización) y `dev_antes_cierre_AAAAMMDD.dmp` (con el borrador resuelto, lista para volver a cerrar). Si el ensayo revela un fallo del código, corrígelo y vuelve a `dev_antes_cierre`, que es lo que más se repite.

Si un script SQL falla a medias en DEV: se restaura `pro_AAAAMMDD.dmp` y se vuelve a empezar desde la sección 3. Un script que falla en DEV es justo para lo que sirve el ensayo: **corrige el script antes de usarlo en PRO**.

## 8. Plan del día real (1 de julio de 2027)

El cierre real solo se puede lanzar **desde el 1/7/2027** (en PRO `app.cierre-temporada.permitir-antes-de-inicio=false`) y solo lo hace un usuario SUPER. Los scripts SQL y el despliegue ya tienen que estar hechos semanas antes (sección 3): el 1 de julio no se ejecuta ningún script.

**Semana anterior**
1. Resumen del cierre sin bloqueos en el panel (sin jugadores sin fila, sin pendientes, sin años de nacimiento) y, si hay pruebas, con su destino claro.
2. Última revisión de la sección 6.4 y 6.5 en PRO (solo lectura).
3. Comprobar que PRO corre el perfil `prod`, con `app.push.habilitado=true` y `app.cierre-temporada.permitir-antes-de-inicio=false`.
4. Avisar al coordinador de que durante la ventana del cierre no debe tocar plantillas, equipos ni horarios.

**El día, en una hora de poco uso (por la mañana temprano)**
1. **Copia de seguridad**: sección 1.2 y 1.3, con fecha y hora. Comprueba el `successfully completed` del log y guarda una copia fuera del servidor. **Sin copia verificada no se cierra.**
2. Consultas de la sección 6 en PRO (solo lectura) → `pro_antes.txt`.
3. Panel → Nueva temporada → revisar el resumen del cierre una última vez y anotarlo.
4. **Cerrar temporada** (SUPER): escribe el nombre exacto `2027/2028`. Todo lo del paso 1 al 8 va en una única transacción.
5. Consultas de la sección 6 → `pro_despues.txt`, y compara con el ensayo (sección 6, "qué esperar").
6. Comprobar la app (entrar como familia, entrenador y coordinador) y la web pública (modo verano: "Plantillas en preparación" y resultados/clasificaciones de la temporada anterior) según la sección 5.
7. Confirmar en el log del backend de PRO que salió el aviso "Nueva temporada" (paso 9).

**Si el cierre falla**
- **Antes de confirmar la transacción (pasos 1 a 8):** el error aparece en el panel y en el log y **no queda nada a medias**: se deshace todo, la temporada vieja sigue activa y los datos son los de antes. Compruébalo con las consultas de la sección 6 (deben ser idénticas a `pro_antes.txt`). Lee el mensaje: si es un bloqueo ("No se puede cerrar: …"), se resuelve en el panel y se reintenta; si es un error técnico, no reintentes a ciegas: copia el stack trace del log, restaura DEV desde `dev_antes_cierre` y reprodúcelo allí antes de volver a intentarlo en PRO.
- **Después de confirmar, en el paso 9 (el aviso a los usuarios):** el cierre **ya está hecho** y no se deshace; solo se pierde el aviso, que el log registra como error. Se puede dar a los usuarios con una noticia o comunicación normal desde el panel.
- **El cierre salió bien pero se descubre un problema grave de datos:** restaurar la copia de seguridad (importación sobre PRO, misma receta de la sección 7 pero en `oracle`) pierde lo escrito desde el volcado, así que decide en las primeras horas y avisa al coordinador. Para problemas menores (un jugador en la categoría equivocada, una prueba mal asignada) es mejor corregirlo desde el panel que restaurar.

**Después**
- Julio-agosto: el coordinador asigna entrenadores a los equipos de pretemporada y se entrena con ellos.
- Septiembre: reparto de equipos (genera las cuotas en la primera asignación), reactivar los horarios, enlazar `EQUIPOS_FFCV`, importar la FFCV, crear los equipos que falten. Al importar los primeros partidos, la web sale del modo verano.
- Tareas automáticas: la purga de comunicaciones (03:30) y el job de cuentas sin jugadores (04:00) corren cada noche. Tras el cierre, la primera noche las familias cuyos jugadores quedaron todos de baja recibirán el aviso de desactivación (a los 3 meses se desactiva la cuenta): revisa antes la lista con la sección 6.11 y las bajas del borrador.
