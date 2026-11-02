# Idioma de la cuenta (contrato para la app)

Los avisos (bandeja y push) y los emails de cuenta se generan en el idioma que la cuenta tiene guardado en el servidor:
`es` (castellano), `ca` (valenciano) o `en` (inglés). La app le dice al servidor qué idioma usa el usuario en tres momentos.
Textos y cuándo se envía cada aviso: [avisos.md](avisos.md).

Valores admitidos: `"es"`, `"ca"`, `"en"` (sin distinguir mayúsculas). **Cualquier otro valor se guarda como `"es"`**. Si la
petición no trae el campo `idioma` (versiones de la app que no lo envían), el idioma de la cuenta no se toca: las cuentas
existentes están en `"es"`, como hasta ahora.

## 1. Al iniciar sesión

`POST /api/app/auth/login`: campo opcional `idioma` en el cuerpo.

```json
{ "email": "ana@example.com", "password": "secreto123", "idioma": "ca" }
```

Si el login es correcto, se guarda el idioma. La respuesta no cambia.

También acepta el campo opcional `idioma` (y lo guarda) `POST /api/app/auth/activar` y `POST /api/app/auth/restablecer`,
que inician sesión igual que `/login` (el email de "cuenta activada" y el de "contraseña cambiada" salen en ese idioma).

## 2. Al registrar el token del dispositivo

`POST /api/app/dispositivos`: campo opcional `idioma` junto a `tokenFcm`, `plataforma` y `versionApp`.

```json
{ "tokenFcm": "…", "plataforma": "android", "versionApp": "1.0.12", "idioma": "en" }
```

## 3. Al cambiar el idioma en Ajustes

`PUT /api/app/preferencias/idioma` (con sesión):

```json
{ "idioma": "ca" }
```

Respuesta `200`:

```json
{ "idioma": "ca" }
```

`401` sin sesión válida. El valor devuelto es el guardado (un valor no admitido vuelve como `"es"`).

## 4. Recuperar la contraseña

`POST /api/app/auth/recuperar`: campo opcional `idioma`. El email con el código sale en el idioma que manda la app en esa
petición; si no lo manda, en el de la cuenta. **Esta petición no cambia el idioma guardado** (no hay sesión: cualquiera
podría pedirlo para otro email).

```json
{ "email": "ana@example.com", "idioma": "ca" }
```

## 5. Topics de FCM para quien no tiene cuenta

La app que se suscribe a los topics sin cuenta debe hacerlo por idioma:

| Topic | Contenido |
| --- | --- |
| `resultados_es`, `resultados_ca`, `resultados_en` | Avisos del partido en directo |
| `noticias_es`, `noticias_ca`, `noticias_en` | Aviso de noticia nueva |

Los topics antiguos `resultados` y `noticias` se siguen publicando, en castellano, para las versiones que todavía no se
suscriben por idioma, y se quitarán cuando la versión mínima obligue a actualizar. Una versión nueva se suscribe solo al
topic de su idioma (y se desuscribe de los otros y de los antiguos); si el usuario cambia el idioma, cambia de topic.

## Lo que no cambia

El `tipo`, la `pantalla` y el resto del `data` de los push son los de siempre: la app navega por ellos, no por el texto.
Lo que escribe la gente (motivos, nombres de equipos, rivales, equipajes, título de una noticia) llega tal cual, sin traducir.
