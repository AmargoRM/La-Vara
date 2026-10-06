# Formato JSON de automatizaciones de La Vara

> Este documento es el contrato que permite a otra IA escribir automatizaciones válidas sin ver el código.
> El código que lo implementa está en `com.lavara.automation`, `triggers`, `conditions` y `actions`.
> El test `AutomationJsonTest.ejemploDelDocumento_pruebaVara` lee el ejemplo de la sección 8: si falla, este documento quedó desactualizado.

## 1. Reglas generales

- Texto UTF-8, JSON estándar. Una automatización es un objeto JSON.
- Los nombres de campo distinguen mayúsculas (`onError`, no `onerror`).
- Cada trigger, condición y acción es un objeto con un campo `"type"` que dice qué es. Los valores de `"type"` de este documento **nunca cambian**.
- Los campos marcados "opcional" pueden faltar: se usa el valor por defecto indicado.
- Los campos que La Vara no conoce se ignoran. Así un archivo hecho por una versión más nueva se puede abrir en una más vieja.
- Un `"type"` desconocido, un campo obligatorio que falta o un valor fuera de rango hacen que la automatización se rechace entera.
- Las horas se escriben como texto `"HH:mm"` en formato de 24 horas: `"08:00"`, `"22:30"`. Van de `"00:00"` a `"23:59"`.
- Las fechas guardadas (`createdAt`, etc.) son milisegundos desde el 1 de enero de 1970 en UTC.

## 2. Automatización

Se lee así: **CUANDO** pasa `trigger`, **SI** se cumplen todas las `conditions`, **HACER** las `actions` en orden.

| Campo | Tipo | Obligatorio | Por defecto | Qué es |
|---|---|---|---|---|
| `id` | texto | sí | — | Identificador único, sin espacios recomendado (`"prueba-vara"`). No puede estar vacío. Lo usa `run_automation`. |
| `name` | texto | sí | — | Nombre visible. |
| `description` | texto | no | `""` | Nota libre. |
| `enabled` | booleano | no | `true` | `false` = no se dispara sola. Se puede ejecutar a mano, pero el historial dirá que está desactivada y no hará nada. |
| `priority` | entero | no | `0` | Si varias responden al mismo evento, corre primero la de número mayor. |
| `trigger` | objeto | sí | — | Ver sección 3. Exactamente uno. |
| `conditions` | lista | no | `[]` | Ver sección 4. Se tienen que cumplir **todas**. Lista vacía = siempre. |
| `actions` | lista | no | `[]` | Ver sección 5. Se hacen en orden, una tras otra. |
| `onError` | texto | no | `"stop"` | Ver sección 6. |
| `cooldownSeconds` | entero ≥ 0 | no | `0` | Segundos mínimos entre dos ejecuciones. `0` = sin espera. |
| `createdAt`, `updatedAt` | entero | no | `0` | Los completa la app. |
| `lastExecutedAt` | entero o `null` | no | `null` | Los completa la app. |
| `executionCount`, `failureCount` | entero | no | `0` | Los completa la app. |

Al escribir una automatización a mano, alcanza con `id`, `name`, `trigger` y `actions`.

## 3. Triggers (cuándo)

### `time`: a una hora fija

```json
{ "type": "time", "time": "08:00", "days": ["monday", "wednesday", "friday"] }
```

- `time` (obligatorio): hora `"HH:mm"`.
- `days` (opcional, por defecto `[]`): días en que corre. Lista vacía = todos los días. Valores: `"monday"`, `"tuesday"`, `"wednesday"`, `"thursday"`, `"friday"`, `"saturday"`, `"sunday"`.

### `battery`: la batería cruza un umbral

```json
{ "type": "battery", "threshold": 20, "direction": "below" }
```

- `threshold` (obligatorio): porcentaje de 0 a 100.
- `direction` (obligatorio): `"below"` = cuando baja hasta el umbral o menos; `"above"` = cuando sube hasta el umbral o más.
- Se dispara **una vez al cruzar** el umbral, no en cada cambio mientras la batería sigue del mismo lado.
- Si al activarla la batería ya está del otro lado del umbral, no se dispara hasta el próximo cruce.
- Mientras haya una automatización activa con `battery` o `power`, el teléfono muestra la notificación fija "La Vara está activa" (exigencia de Android, ver `LIMITES_ANDROID.md`).

### `power`: se conecta o desconecta el cargador

```json
{ "type": "power", "event": "connected" }
```

- `event` (opcional, por defecto `"connected"`): `"connected"` = al enchufar el cargador (cable o base inalámbrica); `"disconnected"` = al desenchufarlo.
- Se dispara una vez por cada conexión o desconexión.

### `location`: llegar a un lugar o irse de él

```json
{ "type": "location", "latitude": 9.9281, "longitude": -84.0907, "radiusMeters": 200, "transition": "enter", "placeName": "Casa" }
```

- `latitude` (obligatorio, -90 a 90) y `longitude` (obligatorio, -180 a 180): centro de la zona, en grados decimales (WGS84, como Google Maps).
- `radiusMeters` (opcional, por defecto `200`): radio del círculo, de 100 a 50000 metros.
- `transition` (opcional, por defecto `"enter"`): `"enter"` = al llegar; `"exit"` = al irse.
- `placeName` (opcional, por defecto `""`): nombre para mostrar, como `"Casa"`.
- `dwellMinutes` (opcional, por defecto `0`, de 0 a 240): solo con `"enter"`. Si es mayor que 0, se dispara cuando el teléfono lleva ese tiempo adentro de la zona, no al entrar. Con `"exit"` se ignora.
- Si el teléfono ya está adentro cuando se guarda, no se dispara hasta salir y volver a entrar.
- Necesita el permiso de ubicación "Permitir todo el tiempo" y la ubicación del teléfono encendida. Android puede tardar unos minutos en notar la entrada o salida.

### `bluetooth`: al conectar o desconectar un aparato Bluetooth

```json
{ "type": "bluetooth", "deviceAddress": "00:11:22:AA:BB:CC", "deviceName": "Carro", "event": "connected" }
```

- `deviceAddress` (opcional, por defecto `""`): dirección del aparato. Vacío = cualquier aparato.
- `deviceName` (opcional, por defecto `""`): nombre para mostrar.
- `event` (opcional, por defecto `"connected"`): `"connected"` o `"disconnected"`.
- Necesita el permiso "Dispositivos cercanos" (Android 12+). Funciona con La Vara cerrada.

### `wifi`: al conectarse o desconectarse de una red Wi-Fi

```json
{ "type": "wifi", "ssid": "Casa", "event": "connected" }
```

- `ssid` (opcional, por defecto `""`): nombre de la red, sin importar mayúsculas. Vacío = cualquier red.
- `event` (opcional, por defecto `"connected"`): `"connected"` o `"disconnected"`.
- Usa la notificación fija "La Vara está activa". Para reconocer la red por nombre hace falta la ubicación "Permitir todo el tiempo".

### `notification`: al llegar una notificación de otra app

```json
{ "type": "notification", "packageName": "com.whatsapp", "appName": "WhatsApp", "textContains": "llegué" }
```

- `packageName` (opcional, por defecto `""`): paquete de la app. Vacío = cualquier app (nunca las de La Vara).
- `appName` (opcional): solo para mostrar.
- `textContains` (opcional, por defecto `""`): el título o el texto tienen que contenerlo, sin importar mayúsculas. Vacío = cualquier notificación.
- Necesita el permiso "Acceso a notificaciones". Se ignoran las notificaciones fijas (en curso) y los resúmenes de grupo. El contenido nunca se guarda ni va a los registros.

### `nfc`: al acercar una etiqueta NFC

```json
{ "type": "nfc", "tagId": "3f9a1c0b7e", "tagName": "Mesa de noche" }
```

- `tagId` (obligatorio para que funcione): código que La Vara grabó en la etiqueta como `lavara://nfc/<tagId>`. Vacío = no responde a ninguna etiqueta.
- `tagName` (opcional): solo para mostrar.
- Android solo lee etiquetas con la pantalla encendida y desbloqueada.

### `manual`: solo a mano

```json
{ "type": "manual" }
```

No se dispara sola. Corre con el botón de ejecutar, con un atajo o desde otra automatización (`run_automation`).
Cualquier automatización, tenga el trigger que tenga, también se puede ejecutar a mano.

## 4. Condiciones (si)

### `battery_level`: nivel de batería

```json
{ "type": "battery_level", "comparison": "greater_than", "value": 20 }
```

- `comparison` (obligatorio): `"greater_than"` (>), `"greater_or_equal"` (≥), `"less_than"` (<), `"less_or_equal"` (≤), `"equal"` (=).
- `value` (obligatorio): porcentaje de 0 a 100.
- Si el teléfono no informa la batería, la condición **no** se cumple.

### `time_between`: la hora actual está en un rango

```json
{ "type": "time_between", "start": "22:00", "end": "06:00" }
```

- `start` (obligatorio) se incluye; `end` (obligatorio) no se incluye.
- Si `end` es menor que `start`, el rango cruza la medianoche: el ejemplo vale de 22:00 a 05:59.

### `wifi_connected`: conectado a un Wi-Fi

```json
{ "type": "wifi_connected", "ssid": "Casa" }
```

- `ssid` (opcional, por defecto `""`): nombre de la red, sin importar mayúsculas. Vacío = cualquier Wi-Fi.
- Si Android no dice el nombre (falta el permiso de ubicación), solo se cumple con `ssid` vacío.

### `charging`: cargador conectado o no

```json
{ "type": "charging", "charging": true }
```

- `charging` (opcional, por defecto `true`): `true` = con el cargador conectado; `false` = sin cargador.
- Si no se puede leer, no se cumple.

### `days_of_week`: solo ciertos días

```json
{ "type": "days_of_week", "days": ["monday", "tuesday", "wednesday", "thursday", "friday"] }
```

- `days` (opcional, por defecto `[]`): días en inglés y minúscula, como en `time`. Lista vacía = cualquier día.

### `and`: todas

```json
{ "type": "and", "conditions": [ { "type": "battery_level", "comparison": "less_than", "value": 50 }, { "type": "time_between", "start": "08:00", "end": "18:00" } ] }
```

Lista vacía = se cumple.

### `or`: al menos una

```json
{ "type": "or", "conditions": [ { "type": "time_between", "start": "06:00", "end": "08:00" }, { "type": "time_between", "start": "18:00", "end": "20:00" } ] }
```

Lista vacía = no se cumple.

### `not`: lo contrario

```json
{ "type": "not", "condition": { "type": "battery_level", "comparison": "equal", "value": 100 } }
```

`and`, `or` y `not` se pueden anidar sin límite.

## 5. Acciones (hacer qué)

### `show_notification`: mostrar una notificación

```json
{ "type": "show_notification", "title": "Batería", "text": "Quedan %battery % a las %time" }
```

- `title` (obligatorio), `text` (opcional, por defecto `""`). Ambos aceptan variables (sección 7).

### `open_app`: abrir una app

```json
{ "type": "open_app", "packageName": "com.whatsapp" }
```

- `packageName` (obligatorio, no vacío): el nombre de paquete de la app, no su nombre visible (Waze es `com.waze`). En el editor se elige de la lista de apps instaladas.

### `open_url`: abrir un enlace

```json
{ "type": "open_url", "url": "https://waze.com/ul?q=San%20Jos%C3%A9" }
```

- `url` (obligatorio): enlace completo que empieza con `http://` o `https://`. Se abre en el navegador o en la app que maneje ese enlace.
- En los registros solo aparece el sitio (`waze.com`), nunca el enlace completo, porque puede llevar claves.
- Si La Vara está en segundo plano y no tiene el permiso "Mostrar sobre otras apps", se muestra una notificación para abrirlo a mano (igual que `open_app`).

### `delay`: esperar

```json
{ "type": "delay", "seconds": 30 }
```

- `seconds` (obligatorio, ≥ 0; el editor permite hasta 86400 = 24 horas). Espera antes de la acción siguiente sin trabar el teléfono.
- Hasta 10 segundos espera ahí mismo. Más de 10 segundos: las acciones siguientes se programan con una alarma exacta y siguen a esa hora, aunque La Vara esté cerrada o el teléfono se reinicie. Si la automatización se desactiva, se borra o se editan sus acciones mientras espera, no sigue.
- Una espera larga dentro de una automatización llamada con `run_automation` no detiene a la que la llamó.

### `run_automation`: ejecutar otra automatización

```json
{ "type": "run_automation", "automationId": "otra-automatizacion" }
```

- `automationId` (obligatorio): el `id` de la otra.
- La otra revisa sus propias condiciones, pero ignora su trigger y su `cooldownSeconds`.
- Falla si la otra no existe, está desactivada o ya se está ejecutando.
- **Ciclos:** si A ejecuta a B y B ejecuta a A, La Vara lo detecta, se detiene y lo registra como error (`Ciclo detectado: a → b → a`).

### `flashlight`: linterna

```json
{ "type": "flashlight", "on": true }
```

- `on` (por defecto `true`): `true` la enciende, `false` la apaga. No pide permisos.
- Android puede apagarla si cierra La Vara en segundo plano (por verificar en el teléfono).

### `set_volume`: volumen

```json
{ "type": "set_volume", "stream": "media", "percent": 40 }
```

- `stream` (por defecto `"media"`): `"media"` (multimedia), `"ring"` (timbre), `"notification"` (notificaciones) o `"alarm"` (alarma).
- `percent` (por defecto 50): de 0 a 100. Se redondea al paso de volumen más cercano del teléfono.

### `set_ringer_mode`: modo de sonido

```json
{ "type": "set_ringer_mode", "mode": "vibrate" }
```

- `mode` (por defecto `"vibrate"`): `"normal"`, `"vibrate"` o `"silent"`.
- `"silent"` necesita el permiso "Acceso a No molestar".

### `do_not_disturb`: No molestar

```json
{ "type": "do_not_disturb", "mode": "priority" }
```

- `mode` (por defecto `"priority"`): `"off"` (apagado), `"priority"` (solo prioridad), `"alarms"` (solo alarmas) o `"silence"` (silencio total).
- Necesita el permiso "Acceso a No molestar".

### `set_brightness`: brillo de pantalla

```json
{ "type": "set_brightness", "percent": 60, "auto": false }
```

- `auto` (por defecto `false`): `true` pone el brillo automático y se ignora `percent`.
- `percent` (por defecto 50): de 1 a 100.
- Necesita el permiso "Modificar ajustes del sistema".

### `open_system_panel`: abrir el interruptor de una función

```json
{ "type": "open_system_panel", "panel": "wifi" }
```

- `panel` (por defecto `"wifi"`): `"wifi"`, `"mobile_data"`, `"bluetooth"`, `"location"`, `"nfc"` o `"airplane_mode"`.
- Android no deja a ninguna app encender ni apagar estas funciones sola (ver `docs/LIMITES_ANDROID.md`). La Vara abre el interruptor y el usuario lo toca.
- Igual que `open_app`: con La Vara en segundo plano necesita "Mostrar sobre otras apps"; si no, muestra una notificación para abrirlo.

### `whatsapp_message`: abrir WhatsApp en el chat de alguien con el mensaje escrito

```json
{ "type": "whatsapp_message", "phone": "+506 8888 7777", "text": "Voy en camino (%time)", "contactName": "Mamá" }
```

- `phone` (por defecto `""`): con código de país (`506` para Costa Rica). Acepta espacios, guiones y `+`.
- `text` (por defecto `""`): el mensaje. Acepta variables (sección 7).
- `contactName` (por defecto `""`): solo para mostrar. En los registros aparece este nombre o los últimos 4 dígitos, nunca el mensaje.
- **No envía solo:** WhatsApp no deja que otra app envíe sin que el usuario toque Enviar. Para eso hace falta Accesibilidad (ver `docs/LIMITES_ANDROID.md`).
- Usa WhatsApp; si no está, WhatsApp Business. Igual que `open_app`: con La Vara en segundo plano necesita "Mostrar sobre otras apps".

### `send_sms`: enviar un SMS sin tocar nada

```json
{ "type": "send_sms", "phone": "8888 7777", "text": "Batería en %battery %", "contactName": "Mamá" }
```

- `phone` (por defecto `""`): como se marcaría en el teléfono (con o sin código de país).
- `text` (por defecto `""`, obligatorio en el editor): acepta variables. Si es largo se divide en varios SMS.
- `contactName` (por defecto `""`): solo para mostrar.
- Necesita el permiso de SMS. Sale por la SIM elegida para SMS y cuesta como un SMS normal. Si el operador lo rechaza (sin señal, sin saldo), queda un error en los registros.

### `dial_number`: abrir el teléfono con un número marcado

```json
{ "type": "dial_number", "phone": "8888 7777", "contactName": "Mamá" }
```

- `phone` y `contactName` como en `send_sms`. El usuario toca Llamar; La Vara no llama sola.

### `navigate`: navegar hacia un lugar

```json
{ "type": "navigate", "destination": "Mall San Pedro", "app": "waze" }
```

- `destination` (por defecto `""`): dirección, nombre de un lugar o coordenadas `"9.9325,-84.0796"`.
- `app` (por defecto `"waze"`): `"waze"` o `"google_maps"`. Sin Waze instalado, el enlace de Waze se abre en el navegador.

### `tap_in_app`: tocar un botón dentro de otra app

```json
{ "type": "tap_in_app", "packageName": "com.whatsapp", "button": "Enviar", "waitSeconds": 5 }
```

- `packageName` (por defecto `""`): la app donde toca. **Tiene que estar en la lista de apps permitidas** que el usuario elige en La Vara; si no, la acción falla. Importar un JSON no agrega apps a esa lista.
- `button` (por defecto `""`): el texto del botón o su nombre para lectores de pantalla (en WhatsApp, la flecha de enviar es `"Enviar"`). También acepta el final de su nombre interno (`"send"`).
- `waitSeconds` (por defecto 5, de 1 a 10): cuánto espera a que la app y el botón aparezcan.
- Necesita el permiso de Accesibilidad encendido y el teléfono desbloqueado con la app a la vista. Va después de la acción que abre la app (por ejemplo, `whatsapp_message` y luego `tap_in_app`).
- En los registros solo aparece el botón que se pidió, nunca lo que hay en la pantalla.

### `vibrate`: vibrar

```json
{ "type": "vibrate", "millis": 500 }
```

- `millis` (por defecto 500, de 1 a 10000): cuánto vibra, en milisegundos. No pide permisos especiales.

### `copy_to_clipboard`: copiar un texto

```json
{ "type": "copy_to_clipboard", "text": "Llegué a las %time" }
```

- `text` (obligatorio, acepta variables): lo que queda copiado. Nunca se escribe en los registros.

### `share_text`: abrir el menú Compartir con un texto

```json
{ "type": "share_text", "text": "Ya voy en camino" }
```

- `text` (obligatorio, acepta variables). Abre el menú Compartir de Android para elegir a qué app mandarlo. Como abre una pantalla, con el teléfono bloqueado espera el desbloqueo.

### `reply_notification`: responder desde una notificación

```json
{ "type": "reply_notification", "packageName": "com.whatsapp", "text": "Voy manejando", "from": "Esteban" }
```

- `packageName` (obligatorio): app de la notificación.
- `text` (obligatorio): la respuesta. Acepta `%battery`, `%time` y `%date`.
- `from` (por defecto `""`): parte del título de la notificación (el contacto o el grupo). Vacío = la más nueva de esa app.
- Usa el botón "Responder" de la notificación más nueva que coincida, sin abrir la app. Funciona con el teléfono bloqueado. Necesita "Acceso a notificaciones".

### `tap_notification_button`: tocar un botón de una notificación

```json
{ "type": "tap_notification_button", "packageName": "com.whatsapp", "button": "Marcar como leído", "from": "" }
```

- `packageName` y `button` obligatorios; `from` como en `reply_notification`. El botón se busca por su texto, como en `tap_in_app`.

### `media_control`: controlar la música

```json
{ "type": "media_control", "command": "play", "packageName": "com.spotify.music" }
```

- `command` (por defecto `"play_pause"`): `"play_pause"`, `"play"`, `"pause"`, `"next"` o `"previous"`.
- `packageName` (por defecto `""`): vacío = como los botones de los audífonos (la app que esté sonando o la última que sonó). Con una app = La Vara le habla directo a esa app, aunque esté cerrada: primero por su servicio de música (`MediaBrowserService`), si no, con la tecla de música enviada solo a ella. Con `"play"` comprueba durante 3 s que algo suene; si no, la acción falla.

### `if`: si… / si no…

```json
{ "type": "if",
  "conditions": [ { "type": "charging", "charging": true } ],
  "matchAll": true,
  "then": [ { "type": "flashlight", "on": true } ],
  "otherwise": [ { "type": "vibrate", "millis": 300 } ] }
```

- `conditions` (por defecto `[]`): las mismas condiciones de la sección 4. Lista vacía = se cumple.
- `matchAll` (por defecto `true`): `true` = tienen que cumplirse todas; `false` = basta con una.
- `then` (por defecto `[]`): acciones si se cumple. `otherwise` (por defecto `[]`): acciones si no.
- Dentro de `then` y `otherwise` las esperas (`delay`) son de 10 segundos como máximo; más largas fallan.
- El historial dice qué camino tomó ("→ se cumple" o "→ no se cumple").


Qué pasa si una acción falla (por ejemplo, la app a abrir no está instalada):

- `"stop"` (por defecto): se registra el error y **no** se hacen las acciones que faltan.
- `"continue"`: se registra el error y se sigue con la acción siguiente.

En los dos casos la ejecución queda en el historial como fallida, con la acción que falló y el mensaje de error.

## 6b. Archivo de respaldo

El botón "Guardar" del menú ☰ → Respaldo crea un archivo así:

```json
{ "format": "la-vara-respaldo", "version": 1, "exportedAt": 1791000000000, "automations": [ { "id": "a-1", "name": "…", "trigger": { "type": "manual" } } ] }
```

"Cargar" acepta ese archivo, una lista de automatizaciones o una sola. Nunca borra ni reemplaza: si ya existe una con el mismo `id` y el mismo contenido, se salta; si existe con cambios, se agrega como copia desactivada con "(importada)" en el nombre. Una automatización que no se entiende no impide cargar las demás.

## 7. Variables

Se reemplazan en `title` y `text` de `show_notification`, en el momento de ejecutar:

| Variable | Valor | Ejemplo |
|---|---|---|
| `%battery` | nivel de batería, sin el signo % (`?` si no se conoce) | `85` |
| `%time` | hora actual, `HH:mm` | `08:00` |
| `%date` | fecha actual, `dd/MM/yyyy` | `05/10/2026` |

Son de solo lectura: no se pueden crear ni cambiar.

## 8. Ejemplo completo

"Prueba Vara": todos los días a las 08:00, si la batería está por encima de 20 %, mostrar una notificación.

```json
{
  "id": "prueba-vara",
  "name": "Prueba Vara",
  "trigger": { "type": "time", "time": "08:00" },
  "conditions": [
    { "type": "battery_level", "comparison": "greater_than", "value": 20 }
  ],
  "actions": [
    { "type": "show_notification", "title": "Prueba Vara", "text": "Batería %battery % a las %time" }
  ]
}
```

## 9. Errores frecuentes al importar

_Pendiente (S5)._
