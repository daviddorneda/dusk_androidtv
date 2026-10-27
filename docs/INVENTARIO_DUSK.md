# Inventario del código de Dusk (tvOS) y orden de port

Basado en el ZIP de `marvinvr/dusk-player` (licencia MIT, © 2026 Marvin von Rappard).
Cifras medidas sobre `Dusk/Sources`: **294 archivos Swift, ~89.500 líneas**.

## Qué hay que portar y qué no

| Módulo | Líneas | Decisión |
|---|---:|---|
| Features/Player + Playback | 19.0k + 10.4k | Portar (la parte más grande). Motor: libVLC primero; Media3 solo si hace falta para HDR/Dolby Vision |
| PlexService (+ MultiServer) | 8.4k | Portar: auth, servidores, hubs, biblioteca, búsqueda, timeline, live TV |
| Features/Detail | 7.4k | Portar |
| Features/Home | 5.7k | Portar (héroe cinemático + hubs) |
| Models | 5.6k | Portar (modelos de Plex con kotlinx.serialization) |
| Features/Libraries | 4.9k | Portar |
| Features/Settings | 4.4k | Portar la parte tvOS (`SettingsTVView`, `TVSettingsComponents`) |
| Shared | 3.9k | Portar (tarjetas, carruseles, formatos) |
| App | 2.4k | Portar (shell de pestañas, rutas, conexión a servidores) |
| Features/Playlists, Photos, LiveTV, Search, Watchlist, Seerr, Account | ~6k en total | Portar por fases |
| Downloads (4.4k + 1.1k UI) | 5.5k | **No portar**: en tvOS están ocultas (`DownloadsFeature.isVisible`) |
| Features/Supporter (StoreKit) | 2.2k | No portar (compras de Apple) |
| Analytics | 0.3k | **No portar tal cual**: envía eventos al servidor del autor; en este port no se envía nada |
| Siri | 1.2k | Sin equivalente directo (Asistente de Google, más adelante) |
| TopShelf | 0.4k | Equivalente: canal "Watch Next" de Android TV |
| Rutas iOS (`*IOS*`, ~1.3k) | 1.3k | No portar |

## Hallazgos que afectan a la fidelidad "100%"

1. **El diseño usa controles nativos de tvOS** (Liquid Glass `.glass`, foco del sistema con elevación, paralaje y sombra, barra de pestañas flotante). Android TV no los tiene: se imitan a mano (ya hecho para el botón principal), pero el resultado será muy parecido, no idéntico.
2. **Medidas en puntos de tvOS** (lienzo 1920x1080). En Android TV se escalan con `ancho_dp / 1920` para que todo se vea igual en cualquier televisor (`TvMetrics` en `DuskTheme.kt`).
3. **Tipografía:** el original usa SF Pro (licencia de Apple, no se puede incluir). Ahora se usa la fuente del sistema con los mismos tamaños; lo más cercano libre es **Inter** (OFL), que se puede añadir al repo después.
4. **Paleta:** neutra a propósito. El coral `#FF6B4A` solo va en logo, icono y página Supporter, nunca en estado de la interfaz.
5. **Reproductor híbrido:** AVPlayer para MP4/MOV y VLCKit para el resto, con remux HLS para Atmos/HDR y conversión Dolby Vision 7 → 8.1 mediante un crate de Rust. En Android no hay AVPlayer: se parte de libVLC para todo y se evalúa Media3 más adelante. La conversión Dolby Vision y el build propio de TrueHD (repo privado del autor) no se portan al principio.
6. **Servidores múltiples:** Home y búsqueda fusionan todos los servidores a la vez (`HubMerge`, `ContinueWatchingMerge`, `SearchMerge`). Hay que diseñarlo desde el principio, no añadirlo luego.

## Orden recomendado

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Tema (colores, tipografía, escala tvOS) y login con PIN de plex.tv/link | Hecha (tanda 1) |
| 2 | Recursos de plex.tv, descubrimiento y sondeo de servidores, selector de perfil de Plex Home | Hecha (tanda 2) |
| 3 | Shell de pestañas (Home, Movies, TV Shows, Search, Settings) y Home con héroe y hubs | **Tanda 3 (esta entrega)** |
| 4 | Bibliotecas, detalle de película/serie/episodio, búsqueda | Pendiente |
| 5 | Reproductor: reproducción directa, pistas, reanudar, timeline, saltar intro/créditos, Up Next | Pendiente |
| 6 | Ajustes, Live TV, Playlists, Fotos, Seerr, Watchlist, Watch Next | Pendiente |

## Archivos de referencia en el original
- Estilo: `STYLE.md` y `Dusk/Sources/Shared/DuskTypography.swift`
- Login tvOS: `Features/Account/SignInView.swift`, `PlexService/PlexService+Auth.swift`
- Capturas de Apple TV: `screenshots/1.7/appletv/` (home, biblioteca, formatos, barra de reproducción, búsqueda, Up Next)
