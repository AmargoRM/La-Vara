# Límites reales de Android para La Vara

Registro vivo. Antes de implementar algo, revisar aquí. Si se descubre un límite nuevo, agregarlo en el mismo PR.
Estado: **Verificado** (probado en el Motorola), **Por verificar** (según documentación, falta probar en el teléfono), **Descartado** (no es posible).

| Función | Límite | API / versión | Alternativa elegida | Estado |
|---|---|---|---|---|
| Alarma a hora exacta | Las alarmas inexactas pueden retrasarse muchos minutos. `SCHEDULE_EXACT_ALARM` viene negado por defecto en instalaciones nuevas desde Android 14. | AlarmManager, Android 12+/14+ | Declarar `USE_EXACT_ALARM` (se concede al instalar; la restricción es solo de política de Play y la app no se publica). Si no se concede, pedir `SCHEDULE_EXACT_ALARM` llevando al ajuste. Comprobar siempre con `canScheduleExactAlarms()`. | Por verificar |
| Alarmas tras reinicio | Android borra todas las alarmas al reiniciar. | BOOT_COMPLETED | Receiver que reprograma todo. También en `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`. | Por verificar |
| Trabajo en segundo plano en Motorola | El fabricante puede cerrar apps en segundo plano. | Optimización de batería | Pedir `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con explicación previa (permitido al no estar en Play). | Por verificar |
| Abrir una app desde segundo plano | Desde Android 10, una app en segundo plano no puede abrir actividades libremente. | Background activity launch restrictions, Android 10+ | Opción A: permiso "Mostrar sobre otras apps" (`SYSTEM_ALERT_WINDOW`), que habilita el inicio desde segundo plano. Opción B: notificación que abre la app al tocarla. Ofrecer A con explicación y caer a B si no se concede. | Por verificar |
| Mostrar notificaciones | Requiere permiso en tiempo de ejecución. | `POST_NOTIFICATIONS`, Android 13+ | Pedirlo la primera vez que una automatización use una notificación. | Por verificar |
| Detectar app abierta | Android no emite un evento cuando se abre una app. | UsageStatsManager / AccessibilityService | Fuera del MVP. Futuro: consultar `UsageStatsManager` solo cuando la pantalla está encendida y con intervalo largo, o accesibilidad. | Por verificar |
| Ajustes restringidos en apps instaladas desde APK | En Android 13+, las apps instaladas desde el navegador o un archivo no pueden activar accesibilidad ni acceso a notificaciones hasta que el usuario permite "ajustes restringidos" en la info de la app. | Restricted settings, Android 13+ | Mostrar instrucciones paso a paso en la pantalla de Permisos: Info de la app → menú ⋮ → Permitir ajustes restringidos. | Por verificar |
| Activar/desactivar Wi-Fi | Las apps normales no pueden cambiar el Wi-Fi desde Android 10. | `WifiManager.setWifiEnabled`, Android 10+ | Abrir el panel de Wi-Fi del sistema (`Settings.Panel`). | Descartado (alternativa: panel) |
| Brillo | Cambiar el brillo del sistema exige permiso especial. | `WRITE_SETTINGS` | Llevar al ajuste "Modificar ajustes del sistema" la primera vez. | Por verificar |
| Geocercas | Máximo 100 geocercas activas por app. Necesitan ubicación en segundo plano, que en Android 11+ solo se concede desde Ajustes. | Geofencing API, `ACCESS_BACKGROUND_LOCATION` | Filtrar: casos activos o más cercanos. Pedir ubicación en primer plano primero y luego guiar a "Permitir todo el tiempo". | Por verificar |
| NFC | Solo lee con la pantalla encendida y, normalmente, desbloqueada. | NFC | Aceptarlo. Escribir NDEF propio en las etiquetas para que el sistema abra La Vara al tocarlas. | Por verificar |
| Bluetooth | Saber qué dispositivo se conecta exige permiso. | `BLUETOOTH_CONNECT`, Android 12+ | Pedirlo al crear un trigger de Bluetooth. | Por verificar |
| Portapapeles | Las apps en segundo plano no pueden leer el portapapeles desde Android 10. | ClipboardManager | Acción manual "Procesar portapapeles" con la app en primer plano, o recibir el texto por Compartir. | Descartado (alternativa: manual) |
| SMS entrantes | Recibir SMS requiere permisos peligrosos; Play los restringe a apps predeterminadas, pero la app no se publica. | `RECEIVE_SMS` | Etapa 3. Permitido al instalar por APK. | Por verificar |
