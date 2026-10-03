# Privacidad: qué sale del teléfono

Regla del usuario (2026-10-03): ningún dato suyo (automatizaciones, registros, ubicación, contactos, mensajes,
respaldo) sale del teléfono, ni al repositorio ni a nadie. Toda función nueva que use la red se agrega a esta
lista en el mismo PR.

## Conexiones que hace La Vara

| Cuándo | A dónde | Qué manda |
|---|---|---|
| Buscar actualizaciones (cada 12 h y al tocar "Buscar") | `api.github.com` | Pide la versión publicada `ultima` de AmargoRM/La-Vara. Manda el token del usuario y `User-Agent: LaVara`. Nada más. |
| Descargar una versión nueva | `api.github.com` y el enlace temporal de GitHub | Igual que arriba; el token no se reenvía al enlace temporal. |
| Abrir el mapa para marcar un lugar | `tile.openstreetmap.org` | Pide las imágenes del pedazo de mapa que se está mirando. No manda la ubicación del GPS ni las automatizaciones. |

Además, GitHub y OpenStreetMap ven la dirección IP de la conexión, como cualquier página web.

## Lo que no sale

- Respaldo automático de Android (nube de Google y copia a otro teléfono): apagado en el manifiesto
  (`allowBackup="false"` y `@xml/sin_respaldo`).
- "Guardar respaldo" y "Exportar registros" solo escriben donde el usuario elige en ese momento.
- Las zonas de lugar las vigila Google Play Services; La Vara no manda coordenadas a ningún servidor. Si en Ajustes
  está encendida "Precisión de la ubicación de Google", el sistema consulta a Google para ubicar el teléfono; eso es
  de Android, pasa con cualquier app de ubicación y se apaga en Ajustes → Ubicación.
