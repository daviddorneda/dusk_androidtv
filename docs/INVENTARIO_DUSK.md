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

## Fases y registro de tandas

Una **fase** es un bloque de funcionalidad del port; una **tanda** es cada entrega (zip) que se sube al repo. Una fase puede necesitar varias tandas: la primera la construye y las siguientes corrigen lo que sale al probarla en la TV. Una fase se da por cerrada cuando se ha validado en la TCL.

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Tema (colores, tipografía, escala tvOS) y login con PIN de plex.tv/link | Cerrada |
| 2 | Recursos de plex.tv, descubrimiento y sondeo de servidores, selector de perfil de Plex Home | Cerrada |
| 3 | Shell de pestañas (Home, Movies, TV Shows, Search, Settings) y Home con héroe y filas | Casi cerrada (tandas 3, 4 y 5; falta validar la 5) |
| 4 | Bibliotecas, detalle de película/serie/episodio, búsqueda | Pendiente |
| 5 | Reproductor: reproducción directa, pistas, reanudar, timeline, saltar intro/créditos, Up Next | Pendiente |
| 6 | Ajustes completos, Live TV, Playlists, Fotos, Seerr, Watchlist, Watch Next | Pendiente |

| Tanda | Fase | Qué trajo |
|---|---|---|
| 1 | 1 | Esqueleto del proyecto, tema y login con PIN |
| 2 | 2 | Servidores de Plex y selector de perfil de Plex Home con PIN |
| 3 | 3 | Shell de pestañas y Home (héroe y filas), primera versión |
| 4 | 3 (correcciones) | Tema oscuro por defecto, interruptor, navegación vertical, informe de carga por servidor en Settings |
| 5 | 3 (correcciones) | Foco de pósters tipo `.card` de tvOS, desplazamiento suave sin saltos, "More" como pista, subir a la barra de pestañas, barra que vuelve a la pestaña activa, Ajustes con la estructura del original |
| 6 | 4a | Navegación con botón Atrás, detalle de película (héroe, sinopsis, reparto, datos del medio, marcar visto) abierto desde Home; velo y fundido del héroe de Home como en el original |
| 7 | 4a (correcciones) | Resplandor blanco de foco dibujado como la sombra difusa de SwiftUI, detalle de película sin desplazamiento automático y con el resplandor del reparto solo en el avatar, Ajustes navegable con el mando desde la barra |
| 8 | 4b (primera parte) | Pestañas Movies / TV Shows / Videos con rejilla de biblioteca paginada, chips de Género, "Unwatched" y Orden con menús, barra de pestañas que se oculta al bajar |
| 9 | 4c | Página de serie (sirve para serie, temporada y episodio): banner que sigue al episodio enfocado, pastillas de temporada, fila de episodios con progreso, resumen y reparto del episodio; pulsar series, temporadas y episodios en Home y bibliotecas la abre |

### Fase 4 por partes

| Parte | Contenido | Estado |
|---|---|---|
| 4a | Navegación y detalle de película | Tanda 6 |
| 4b | Pestañas Movies / TV Shows: biblioteca en rejilla (ordenar, filtrar, índice por letras) | Tanda 8 (rejilla, género, no vistos, orden); faltan índice A–Z, hoja de filtros y colecciones |
| 4c | Detalle de serie, temporada y episodio | Tanda 9 (una sola página, como tvOS); faltan tráiler, extras, colecciones, valorar y listas |
| 4d | Búsqueda | Pendiente |
| 4e | Recomendaciones por biblioteca, colecciones, ficha de persona, extras y tráiler | Pendiente |

Nota: en el original, la pestaña Movies / TV Shows abre primero las *recomendaciones* de la biblioteca (motor de unas 2.000 líneas) y desde ahí se llega a la rejilla. En el port se empezará por la rejilla (4b) y las recomendaciones llegarán en 4e.

## Decisiones de fidelidad (qué hace el original y cómo se replica)

- **Apariencia:** oscura por defecto en tvOS (el autor lo deja escrito: el modo claro no gusta en esta app). Se replica; el ajuste Light/Dark/System existe por dentro y su botón llegará con los Ajustes.
- **Foco de los pósters:** el original usa el estilo de botón `.card` nativo de tvOS (elevación, sombra suave y brillo). Se aproxima con escala ~1.1×, sombra y brillo (`DUSK_CARD_FOCUS_SCALE` en `Focus.kt`).
- **Foco en filas propias** (selector de usuarios, teclado del PIN, reparto): escala 1.05 en 0.18 s y resplandor blanco, que en el original es la sombra de SwiftUI `.shadow(white 0.34, radio 16, y 6)`. En el port se dibuja como un halo gaussiano con esa misma silueta (no con la elevación de Material, que daba un borde duro). En el reparto la escala afecta a toda la ficha, el halo rodea el avatar y el texto lleva su propio halo.
- **Filas de Ajustes:** banda de foco redondeada (18 pt) al 20 % del color de texto, dentro de tarjetas de 28 pt.
- **"More ⌄" del héroe:** solo una pista visual, no se puede enfocar. Aparece mientras el botón de reproducir tiene el foco y la flecha rebota.
- **Héroe:** izquierda/derecha cambia de elemento; abajo lleva a la primera tarjeta de la primera fila con la página fijada en el "pliegue" (hueco de 84 pt); arriba lleva a la barra de pestañas.
- **Barra de pestañas:** píldora flotante; el contenido cambia al mover el foco entre pestañas; al subir desde el contenido se llega siempre a la pestaña activa.
- **Detalle de película:** héroe de 520–760 pt con el fondo, velo vertical (0.18→0.86) y horizontal (0.86→0), fundido inferior estándar, logo de título de 124 pt, línea de metadatos, acciones (Play / Resume y marcar visto), sinopsis, reparto (avatares de 144 pt) y datos del medio. Al volver desde el detalle el foco vuelve a Home arriba (el original restaura el póster exacto).
- **Clasificación por edades:** el original muestra el texto tal cual lo devuelve Plex (`contentRating`), sin convertirlo en símbolo. Por eso un servidor configurado para España devuelve "es/18". Se replica igual; el valor depende del país de certificación de la biblioteca en Plex.
- **Datos del medio:** el original toma solo la primera versión del medio (`media.first`) y muestra los campos que Plex da a nivel de medio: resolución, códec de vídeo, códec de audio y canales. Es la pista que Plex considera principal (normalmente la predeterminada); no muestra idioma ni distingue pistas. El selector de pistas con idiomas está en el reproductor (fase 5).
- **Fondo de página:** color plano `#090A0F` (`duskTVOSPageBackground`, sin degradado). El degradado que se ve a la izquierda en las capturas del repo es idéntico en pantallas distintas, así que es del marco del montaje y no de la app.
- **Rejilla de biblioteca:** columnas calculadas como `AdaptivePosterGridLayout` (póster preferido de 196 pt, separación 40 pt, margen 48 pt: 7 columnas a 1920 pt). Chips de 18 pt de radio con borde fino. Varias bibliotecas del mismo tipo se recorren una tras otra sin repetir títulos.
- **Series:** tvOS no tiene pantallas separadas de serie, temporada y episodio; las tres rutas abren una sola página (`SeriesDetailView`) que se abre en la temporada pedida (si no, la primera regular con algo sin ver) y en el episodio pedido (si no, el siguiente por ver: primero uno a medias, luego el primero sin ver). El fotograma de un episodio sin ver se desenfoca para no destripar nada; donde el sistema no puede desenfocar (antes de Android 12), el banner usa el fondo de la serie y las miniaturas no se desenfocan.
- **Ajustes:** estructura del original (título, secciones en tarjetas, "Account" con Sign Out en rojo). "Servers" es un añadido provisional de diagnóstico.

## Pendientes conocidos de fidelidad
- Efecto Liquid Glass y paralaje de los pósters: solo aproximados.
- Tipografía: fuente del sistema en lugar de SF Pro (valorar Inter).
- Ajustes: faltan las secciones Support, Playback, Appearance, Seerr y About.
- Home: faltan Live TV, Watchlist y estantes personalizados; orden de filas por biblioteca.
- Bibliotecas: faltan el índice A–Z, la hoja de filtros, las colecciones y las recomendaciones; el orden y "Unwatched" no se recuerdan por biblioteca; el menú de los chips es una lista desplegable propia, no el menú nativo.
- Detalle de película: faltan tráiler/extras, valoraciones por fuente, descarga de subtítulos, valorar, listas, Watchlist, colecciones, selección de versión y reproducir; al volver no se restaura el póster exacto.

## Archivos de referencia en el original
- Estilo: `STYLE.md` y `Dusk/Sources/Shared/DuskTypography.swift`
- Login tvOS: `Features/Account/SignInView.swift`, `PlexService/PlexService+Auth.swift`
- Capturas de Apple TV: `screenshots/1.7/appletv/` (home, biblioteca, formatos, barra de reproducción, búsqueda, Up Next)
