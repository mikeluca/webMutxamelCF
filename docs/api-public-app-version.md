# GET /api/public/app/version

Endpoint público (sin autenticación) que la app consulta al arrancar para saber si debe bloquearse o avisar.

## Respuesta

```json
{
  "minimaAndroid": "1.0.2",
  "minimaIos": "1.0.14",
  "recomendadaAndroid": "1.0.2",
  "recomendadaIos": "1.0.14",
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
| iOS | 1.0.14 | 1.0.14 | 1.0.14 publicada en la App Store el 20/11/2026 |
| Android | 1.0.2 | 1.0.2 | Aún no está en producción |

## Regla

La mínima y la recomendada de cada plataforma **solo se suben cuando esa versión ya se puede descargar en su tienda**. En Android, cuando el lanzamiento en producción está al 100 %. Subirlas antes dejaría a los usuarios bloqueados sin poder actualizar.
