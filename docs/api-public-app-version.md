# GET /api/public/app/version

Endpoint público (sin autenticación) que la app consulta al arrancar para saber si debe bloquearse o avisar.

## Respuesta

```json
{
  "minimaAndroid": "1.0.14",
  "minimaIos": "1.0.14",
  "recomendadaAndroid": "1.0.15",
  "recomendadaIos": "1.0.15",
  "urlAndroid": "https://play.google.com/store/apps/details?id=com.mutxamelcf.app",
  "urlIos": "https://apps.apple.com/es/app/mutxamel-cf/id6820570960"
}
```

- Por debajo de la **mínima** la app se bloquea y manda a la tienda; por debajo de la **recomendada** solo avisa.
- Se configura en `mvc/src/main/resources/application.properties` (`app.version.minima.*`, `app.version.recomendada.*`, `app.url.*`). `app.url.ios` se puede sobrescribir con la variable de entorno `APP_URL_IOS`.
- La pantalla de estado de la app (`EstadoAppService`) marca como "por debajo de la mínima" los dispositivos con una versión anterior a la mínima de su plataforma.

## Valores actuales

| Plataforma | Mínima | Recomendada | Nota |
|---|---|---|---|
| iOS | 1.0.14 | 1.0.15 | 1.0.15 publicada en Google Play (producción) y en la App Store el 24/11/2026 |
| Android | 1.0.14 | 1.0.15 | 1.0.15 publicada en Google Play (producción) y en la App Store el 24/11/2026 |

## Regla

La mínima y la recomendada de cada plataforma **solo se suben cuando esa versión ya se puede descargar en su tienda**. En Android, cuando el lanzamiento en producción está al 100 %. Subirlas antes dejaría a los usuarios bloqueados sin poder actualizar.

---

# GET /api/public/app/funciones

Endpoint público (sin sesión, como `/version`) con las funciones que el servidor tiene activadas. La app puede mostrarlas u ocultarlas sin publicar otra versión. No cambia la respuesta de `/version`.

```json
{ "registroFamiliasHabilitado": false }
```

## Registro autónomo de familias ("Soy familiar de un jugador")

Interruptor en `mvc/src/main/resources/application.properties`:

```
app.registro-familias.habilitado=${APP_REGISTRO_FAMILIAS:false}
```

Está **desactivado** hasta que el club dé el visto bueno.

**Para activarlo:** definir `APP_REGISTRO_FAMILIAS=true` en el contenedor y reiniciar. No hace falta desplegar código ni publicar otra versión de la app. Para volver a cerrarlo, quitar la variable (o ponerla a `false`) y reiniciar.

Con el interruptor a `false`:

- `GET /api/public/app/funciones` devuelve `registroFamiliasHabilitado: false` (la app nueva oculta el botón).
- `POST /api/public/solicitudes-familia` responde **403** antes de crear nada (ni cuenta, ni solicitud, ni email, ni consume el límite de intentos). Es lo que ven las versiones 1.0.16 y anteriores, que siguen mostrando el botón; la app muestra el campo `mensaje`:

```json
{
  "estado": "REGISTRO_NO_DISPONIBLE",
  "mensaje": "El registro de familias estará disponible próximamente. Mientras tanto, pide la invitación a tu entrenador o al club."
}
```

- `POST /verificar` y `POST /reenviar-codigo` **siguen funcionando**, para no dejar colgadas las solicitudes que ya estaban a medias.
- No cambian la solicitud desde dentro de la app con sesión (`/api/app/solicitudes-familia`) ni la revisión desde el panel.
