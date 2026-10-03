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

- `seconds` (obligatorio, ≥ 0). Espera antes de la acción siguiente sin trabar el teléfono.

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

## 6. Política de error

Qué pasa si una acción falla (por ejemplo, la app a abrir no está instalada):

- `"stop"` (por defecto): se registra el error y **no** se hacen las acciones que faltan.
- `"continue"`: se registra el error y se sigue con la acción siguiente.

En los dos casos la ejecución queda en el historial como fallida, con la acción que falló y el mensaje de error.

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
