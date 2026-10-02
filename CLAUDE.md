# La Vara — reglas permanentes para Claude Code

Este archivo se lee al inicio de cada sesión. Contiene reglas, no tareas.
Las tareas están en `docs/ROADMAP.md`. Los límites de Android están en `docs/LIMITES_ANDROID.md`.

## 1. Contexto

- App Android personal de automatización (evento → condiciones → acciones), inspirada conceptualmente en Tasker.
- Paquete `com.lavara`. Nombre visible "La Vara".
- Teléfono objetivo: Motorola Edge 50 Fusion (Android 14 o superior). Debe seguir funcionando en otros Android modernos.
- **No se publica en Google Play.** Se instala desde el APK que genera GitHub Actions. Por eso se pueden usar permisos que Play restringe (alarmas exactas, ignorar optimización de batería, SMS, etc.), siempre con APIs públicas.
- **El usuario no es programador y no usa Android Studio ni terminal.** Prueba cada versión instalando el APK en su teléfono. La pantalla de logs de la app es su única herramienta de diagnóstico.

## 2. Cómo hablarle al usuario (en el chat y en los PR)

- Español, frases completas, sin cortesías ni relleno.
- La primera vez que aparezca un término técnico, agregar justo después una línea aparte con este formato:
  `→ término = qué es (qué hace en este caso concreto).` Máximo 20 palabras.
- Antes de proponer un cambio: qué cambia, por qué y qué se puede romper.
- Si algo es irreversible o peligroso (borrar datos, cambiar la llave de firma, migraciones de base de datos), explicarlo largo y despacio, y pedir confirmación.
- Si el usuario repite una pregunta, la explicación anterior falló: explicar de otra forma, no repetir.

## 3. Stack fijo

- Kotlin, Jetpack Compose con Material 3, Room, kotlinx.serialization, coroutines.
- Un solo módulo `:app` con paquetes separados. No crear módulos Gradle hasta que el ROADMAP lo pida.
- Inyección de dependencias manual con un `AppContainer`. **No** usar Hilt, Koin ni Dagger.
- Versiones centralizadas en `gradle/libs.versions.toml`. Usar las versiones estables más recientes al crear el proyecto y no cambiarlas sin motivo escrito en el PR.
- `minSdk = 29`. `targetSdk` y `compileSdk` = el más alto estable que soporte la AGP elegida.
- Cada librería nueva necesita una justificación de una línea en el PR. Preferir la API de Android antes que una librería.

## 4. Arquitectura

Paquetes dentro de `com.lavara`:

```
core/          tipos base, Result, reloj inyectable, AppContainer
automation/    modelo Automation, AutomationEngine, ExecutionResult
triggers/      Trigger (sealed) + programadores/receptores de cada tipo
conditions/    Condition (sealed) + evaluador
actions/       Action (sealed) + ejecutores
data/          Room: entidades, DAO, base de datos, repositorios
logging/       registro estructurado hacia Room
system/        receivers, alarmas, permisos, servicios Android
ui/            pantallas Compose, navegación, tema
plugins/       solo interfaces (AutomationPlugin); sin implementación todavía
```

Reglas de diseño que no se negocian:

1. **El motor es Kotlin puro.** `AutomationEngine` no importa nada de `android.*`. Recibe un `TriggerEvent`, evalúa condiciones y ejecuta acciones a través de interfaces (`ActionExecutor`, `DeviceState`, `Clock`). Así se prueba con tests JVM rápidos.
2. **Trigger, Condition y Action son `sealed interface` con `@Serializable` y `@SerialName` estable.** El JSON que producen es a la vez:
   - lo que se guarda en Room (columna `definition`),
   - el formato de importar/exportar,
   - el contrato documentado en `docs/FORMATO_JSON.md`.
3. **Nunca cambiar ni borrar un `@SerialName` existente.** Los campos nuevos siempre llevan valor por defecto. Romper esto deja ilegibles las automatizaciones guardadas del usuario.
4. **Room nunca usa `fallbackToDestructiveMigration`.** Ese modo borra la base completa al cambiar el esquema. Cada cambio de esquema lleva una `Migration` escrita a mano y un test. Exportar el esquema (`room.schemaLocation`) al repo.
5. Cada acción es un componente independiente y reutilizable (`OpenAppAction` no sabe nada de qué automatización la usa).
6. Protección en el motor desde el inicio: cooldown y deduplicación por automatización, y detección de ciclos para `RunAutomation` (A → B → A se detiene y se registra).
7. Si una acción falla: se registra (acción, error, hora) y la automatización sigue o se detiene según su política `onError = CONTINUE | STOP`.

## 5. Reglas de Android

- **Antes de implementar cualquier función, revisar `docs/LIMITES_ANDROID.md`.** Si se descubre un límite nuevo, agregarlo a ese archivo en el mismo PR.
- Si algo no se puede hacer con APIs públicas: no fingirlo. Explicar qué API lo limita, desde qué versión, qué permiso pide, y construir la alternativa legítima más cercana.
- Prohibido: APIs privadas, reflexión sobre APIs ocultas, root, exploits, keylogging, vigilancia, evadir permisos.
- Prohibido el polling continuo. Usar en este orden de preferencia: broadcasts del sistema → callbacks → AlarmManager → WorkManager → foreground service (solo si no hay otra salida, y con notificación "La Vara está activa").
- Permisos bajo demanda, nunca todos al primer inicio. Si un trigger o acción no tiene su permiso, su estado es `REQUIRES_PERMISSION` y la UI lleva a la pantalla de ajustes correspondiente.
- Código dependiente de versión encapsulado con `Build.VERSION.SDK_INT`.
- Toda alarma programada se vuelve a programar en `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` y `TIMEZONE_CHANGED`.

## 6. Logs (prioridad alta)

El usuario no puede ver logcat. Todo lo importante va a la tabla `logs`:
- cada evento recibido, cada automatización evaluada (y por qué se ejecutó o no), cada acción con su duración y resultado, cada error con su mensaje,
- cada alarma programada o reprogramada, con la hora prevista.

Nunca escribir en logs tokens, contraseñas, headers de autenticación ni contenido completo de notificaciones privadas.

## 7. Flujo de trabajo

1. Una sesión = una casilla (o un grupo pequeño) de `docs/ROADMAP.md` = un PR pequeño. No adelantar trabajo de otras casillas.
2. No hacer refactors grandes ni tocar funcionalidades no relacionadas.
3. Antes de dar algo por terminado:
   - Intentar compilar y probar en el entorno: `./gradlew assembleDebug testDebugUnitTest`.
   - Si el entorno no puede compilar Android (falta SDK o red), hacer push y revisar el resultado de GitHub Actions con `gh run watch` / `gh run view --log-failed`. Corregir hasta que esté en verde.
   - **Nunca declarar terminada una tarea con la compilación o los tests en rojo.** Nunca decir "debería funcionar".
4. Tests:
   - Lógica pura (motor, condiciones, serialización JSON, cooldown, ciclos): JUnit en `src/test`.
   - Room y receivers: Robolectric en `src/test`, para que corran en CI sin emulador.
   - Sin tests instrumentados por ahora.
5. Al terminar, marcar la casilla en `docs/ROADMAP.md` y escribir la descripción del PR con esta plantilla:

```
## Qué cambia
## Por qué
## Qué se puede romper
## Cómo probarlo en el teléfono (pasos numerados, sin jerga)
## Permisos nuevos
## Límites de Android encontrados
## Siguiente paso recomendado
```

## 8. Seguridad del repo

- **Nunca hacer commit de la llave de firma (`*.jks`, `*.keystore`), contraseñas ni tokens.** Están en GitHub Secrets.
- Tokens que use la app (por ejemplo, para HTTP): Android Keystore, nunca texto plano ni código.
- `.gitignore` debe excluir keystores, `local.properties` y archivos de build.
