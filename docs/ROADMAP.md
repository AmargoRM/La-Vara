# La Vara — Hoja de ruta

Cada casilla es una sesión de Claude Code y un PR. Se trabajan en orden.
Una casilla se marca `[x]` solo cuando CI está en verde y la prueba en el teléfono está escrita en el PR.

---

## ETAPA 1 — MVP

**Criterio de éxito del MVP (la prueba que manda):**
Crear "Prueba Vara": CUANDO 08:00, SI batería > 20 %, HACER mostrar notificación.
Cerrar la app, reiniciar el teléfono, esperar. La notificación llega a la hora. Al abrir La Vara, el historial muestra la ejecución con su detalle.

### S0 — Esqueleto, CI y firma
- [x] Proyecto Android vacío (`com.lavara`) con Compose, tema con color principal `#0F7C73`, modo claro/oscuro del sistema y una pantalla "LA VARA".
- [x] `gradle/libs.versions.toml` con versiones estables actuales.
- [x] `.gitignore` correcto (keystores, `local.properties`, build).
- [x] Workflow `.github/workflows/build.yml`: en cada push y PR compila, corre tests unitarios y, en `main`, firma el APK release y lo publica en un GitHub Release (sobrescribiendo uno llamado `ultima`), para que el usuario lo descargue directo desde el navegador del teléfono.
- [x] `versionCode` = número de ejecución de Actions, para que cada APK se instale como actualización.
- [x] Firma con llave fija leída de GitHub Secrets. El usuario no usa terminal ni Java: diseñar el método más simple para crear la llave una sola vez (por ejemplo, un workflow manual que la genera y la entrega como artifact descargable), y guiarlo paso a paso para guardar la copia de respaldo y cargar los secretos. Explicar despacio por qué perder o cambiar la llave obliga a desinstalar y borra los datos.
- [x] `docs/FORMATO_JSON.md` creado (vacío con estructura), `docs/LIMITES_ANDROID.md` revisado.
- **Prueba en el teléfono:** descargar `ultima`, instalar, abrir, ver "LA VARA". Hacer un segundo push trivial, instalar encima sin desinstalar.

### S0.5 — Actualizaciones dentro de la app (pedido del usuario, agregado antes de S1)
- [x] Token de GitHub de solo lectura guardado cifrado con Android Keystore; se pega una vez en la app.
- [x] Revisión de versión nueva al tocar "Buscar actualización" y cada 12 horas en segundo plano con WorkManager (solo con internet).
- [x] Notificación "Hay una versión nueva" (una vez por versión) que abre La Vara, descarga el APK y abre el instalador de Android.
- [x] Aviso en pantalla y por notificación cuando el token vence en 14 días o menos.
- [x] El Release incluye una marca invisible `versionCode=N` para que la app sepa qué número publicó.
- [x] Tests: lectura de la respuesta de GitHub, comparación de versiones, mensajes de error, vencimiento del token.
- **Prueba en el teléfono:** instalar a mano la primera versión con esta función, pegar el token, publicar otra versión y actualizar desde la notificación.

### S1 — Modelo y motor (Kotlin puro)
- [x] `Automation` (id, name, description, enabled, priority, trigger, conditions, actions, onError, cooldownSeconds, createdAt, updatedAt, lastExecutedAt, executionCount, failureCount).
- [x] `Trigger`, `Condition`, `Action` como sealed interfaces serializables. Tipos iniciales: `Time` (hora + días de semana), `Battery` (umbral, arriba/abajo), `Manual`; condiciones `BatteryLevel`, `TimeBetween`, `And`, `Or`, `Not`; acciones `ShowNotification`, `OpenApp`, `Delay` y `RunAutomation` (adelantada para cumplir la detección de ciclos que CLAUDE.md pide desde el inicio).
- [x] `AutomationEngine`: recibe `TriggerEvent`, filtra activas y compatibles, evalúa condiciones, ejecuta acciones en secuencia sin bloquear, aplica cooldown y deduplicación, devuelve `ExecutionResult` (success, timestamp, automationId, duration, executedActions, failedAction, errorMessage).
- [x] Variables básicas de solo lectura en el contexto: `%battery`, `%time`, `%date`.
- [x] `docs/FORMATO_JSON.md` completo con un ejemplo por tipo. Este documento debe permitir que otra IA escriba automatizaciones válidas sin ver el código.
- [x] Tests: condiciones anidadas, cooldown, deduplicación, ida y vuelta JSON de cada tipo, falla de acción con CONTINUE y STOP.
- **Prueba en el teléfono:** ninguna visible; solo CI en verde.

### S2 — Persistencia, logs e historial
- [x] Room: tablas `automations` (columnas de resumen + `definition` JSON), `automation_runs`, `logs`, `settings`. Esquema exportado al repo.
- [x] Repositorios y `AppContainer`.
- [x] Logger estructurado (nivel, hora, origen, mensaje, automationId opcional).
- [x] Pantalla **Historial**: lista de ejecuciones y de logs, con filtro por automatización y nivel.
- [x] Botón **Exportar logs**: genera un texto y abre el menú Compartir de Android (para pegárselo a Claude).
- [x] Tests Room con Robolectric.
- **Prueba en el teléfono:** abrir Historial, ver el log "app iniciada", exportarlo por WhatsApp o correo.

### S3 — El corazón: trigger de hora exacta
- [x] Programador con `AlarmManager.setExactAndAllowWhileIdle` (o `setAlarmClock` si hace falta). Declarar `USE_EXACT_ALARM` y `SCHEDULE_EXACT_ALARM` según lo que diga `LIMITES_ANDROID.md`.
- [x] Receiver de alarma → `TriggerEvent` → motor → siguiente alarma programada.
- [x] Reprogramación en `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`.
- [x] Acción `ShowNotification` real (canal propio, permiso `POST_NOTIFICATIONS` bajo demanda).
- [x] Condición de batería leyendo el estado real.
- [x] Pedir exclusión de optimización de batería con explicación previa.
- [x] Crear automáticamente la automatización "Prueba Vara" desactivada, como plantilla.
- [x] Controles mínimos en Inicio (activar, cambiar hora, probar ahora) para poder hacer la prueba antes del editor de S4.
- **Prueba en el teléfono:** el criterio de éxito del MVP completo, con la hora puesta 3 minutos adelante.

### S4 — Editor y control
- [x] Dashboard: estado del motor, cantidad de automatizaciones y activas, última ejecución, último evento, errores recientes, lista con interruptor activar/desactivar.
- [x] Editor en tres pasos: ¿CUÁNDO? → ¿SI? → ¿HACER QUÉ?, con reordenar acciones. Formularios simples, sin construcción visual por arrastre todavía.
- [x] Ejecutar manualmente, duplicar, eliminar (con confirmación).
- **Prueba en el teléfono:** crear desde cero "Prueba Vara" con el editor y repetir el criterio de éxito.

### S5 — Cierre del MVP
- [x] Trigger de batería (umbral, cargador conectado/desconectado) por broadcasts.
- [x] Acción `OpenApp` con selector de apps instaladas (nombre, paquete, ícono) y la solución para abrir apps desde segundo plano descrita en `LIMITES_ANDROID.md`.
- [x] Acción `Delay` sin bloquear (corta: dentro de la ejecución; larga: alarma).
- [x] Pantalla **Permisos** con semáforo (hecho: tarjeta en el menú ☰ con verde/rojo y botón a cada ajuste): verde concedido, amarillo opcional, rojo necesario para una automatización existente; cada fila abre su ajuste.
- [x] Importar y exportar automatizaciones como archivo JSON (selector de archivos del sistema). _Falta: desde el portapapeles._
- **Prueba en el teléfono:** exportar todo, desinstalar en un teléfono de prueba o borrar datos, importar, verificar que todo vuelve.

---

## ETAPA 2 — Lo que la vuelve útil (orden por utilidad para el usuario)

- [ ] **S6 Intents de entrada:** otras apps (GPS TICO, Garúa Aforos, atajos del launcher) pueden ejecutar una automatización por nombre o id. Atajos de pantalla de inicio y tile de Ajustes rápidos.
- [ ] **S7 NFC:** _Hecho: disparador por etiqueta grabada por La Vara (`lavara://nfc/<código>`). Falta: pantalla de etiquetas con contador y última lectura._ pantalla "Etiquetas NFC" (nombre, UID, última lectura, automatización asociada, contador). Leer UID y NDEF; escribir NDEF propio para que la etiqueta abra La Vara directamente.
- [ ] **S8 HTTP + JSON:** `HttpAction` (GET/POST/PUT/PATCH/DELETE, headers, body, timeout), guardar respuesta en variables con rutas JSON, variables personalizadas `%nombre`. Tokens en Android Keystore. Plantilla incluida: disparar un workflow de GitHub (`repository_dispatch`).
- [ ] **S9 Ubicación y geocercas:** geocercas manuales, entrar/salir/permanecer X minutos. _Hecho: zona marcada en el mapa (OpenStreetMap) con entrar/salir. Falta: permanecer X minutos, GeoJSON y variables._ Fuente de geocercas desde URL GeoJSON (por ejemplo `casos.geojson` de Nube-amarga), refrescada periódicamente, respetando el límite de 100. Variables `%lat`, `%lon`.
- [ ] **S10 Bitácora de campo:** acción "registrar en bitácora" (hora, punto, automatización, nota) con exportación CSV/GeoJSON.
- [ ] **S11 Compartir hacia La Vara:** recibir texto, URL, ubicación o imagen desde el menú Compartir y pasarlo como variables a una automatización. Convertir coordenadas a CRTM05 (EPSG:5367).
- [x] **S12 Notificaciones entrantes:** `NotificationListenerService` con filtros por app y por texto (título o texto).
- [x] **S13 Bluetooth y Wi-Fi:** conectado/desconectado a un dispositivo emparejado o a una red concreta.
- [x] **S14 Widget** de pantalla de inicio: botón 1×1 por automatización "a mano", con el ícono de la app que abre (o un emoji según la acción) y su nombre abajo.
- [ ] **S15 Más acciones del sistema:** vibrar, sonido, volumen, multimedia, abrir URL, abrir ajuste, compartir texto, copiar al portapapeles, brillo (con permiso especial).
  - [x] Primera parte: linterna, volumen, modo de sonido, No molestar, brillo y abrir el interruptor de Wi-Fi, datos, Bluetooth, ubicación, NFC y modo avión.
- [ ] **S15b Acciones dentro de otras apps** (pedido del usuario, 2026-10-03):
  - [x] Abrir WhatsApp en el chat de un contacto con el mensaje escrito, llamar a un número (marcador) y navegar con Waze o Google Maps.
  - [x] Enviar SMS sin tocar, con permiso bajo demanda.
  - [x] Con el teléfono bloqueado, la automatización que abre algo espera el desbloqueo y corre entera al desbloquear (sin notificación "Tocá para abrir").
  - [x] Tocar botones dentro de otras apps con Accesibilidad, limitado a una lista de apps elegidas por el usuario (OK del usuario el 2026-10-03).
- [ ] **S16 Archivos:** crear, escribir, añadir, leer, copiar, mover, eliminar, existe, dentro de carpetas que el usuario elija (Storage Access Framework).

## ETAPA 3 — Avanzado

- [x] Condiciones nuevas: conectado a un Wi-Fi, cargando o sin cargador, solo ciertos días.
- [ ] Evaluador de expresiones seguro (`%battery < 20`, `contains`, operaciones matemáticas), sin ejecutar código arbitrario.
- [ ] Control de flujo: IF/ELSE, LOOP, WAIT hasta condición con timeout, STOP, RETURN, RunAutomation con protección de ciclos.
- [ ] Sensores (detectar los disponibles en el teléfono antes de ofrecerlos), movimiento/quietud, proximidad, luz.
- [ ] Llamadas y SMS entrantes.
- [ ] Modo Debug/Laboratorio: ver eventos en vivo, inspeccionar variables, probar triggers.
- [ ] Sistema de plugins sobre la interfaz `AutomationPlugin`.
- [ ] AccessibilityService solo para lo que no tenga API pública, explicado en la app.
- [ ] Comandos de voz, OCR, QR, cámara.

## Fuera de alcance

- Publicar en Google Play.
- IA dentro de la app. Las automatizaciones se generan fuera (pidiéndoselas a Claude con `docs/FORMATO_JSON.md`) y se importan.
- Root, APIs privadas, vigilancia.
