# Port de Dusk for Plex (tvOS) a Android TV

Repo origen: https://github.com/marvinvr/dusk-player (MIT, Swift/SwiftUI).
Objetivo: app Android TV (Kotlin + Jetpack Compose for TV) con interfaz y funcionalidad 100% fieles a la versión tvOS. Lo que no exista en Android TV se adapta a su equivalente más cercano.
Dispositivo de prueba: TV TCL con Android TV. Entorno: PC con Windows, solo editor/terminal + Claude Code + Git. **Sin Android Studio: todo se compila en GitHub Actions.**

## 0. Primer paso en el PC (antes de escribir código)
1. Crear el repo nuevo en GitHub y clonar el repo origen en `origen/` (solo lectura, referencia).
2. Leer `README.md`, `CLAUDE.md`, `AGENTS.md`, `STYLE.md`, `project.yml`, `docs/`, `Dusk/` y `screenshots/`.
3. Inventariar cada pantalla y servicio de `Dusk/` en una tabla (vista SwiftUI -> pantalla Compose) y marcar el estado de cada una.
4. Extraer tokens de diseño (colores, tipografías, radios, espaciados, animaciones, comportamiento del foco) a un `Theme.kt` antes de construir pantallas.

## 1. Stack objetivo
| Área | Elección |
|---|---|
| UI | Compose for TV (`androidx.tv:tv-material`), Navigation Compose |
| Reproductor | libVLC para Android (`org.videolan.android:libvlc-all`): mismo motor que VLCKit, mismo comportamiento de códecs, pistas y subtítulos |
| Red / Plex API | OkHttp + Retrofit + kotlinx.serialization (login con PIN de plex.tv, descubrimiento de servidores, conexiones locales/remotas/relay) |
| Persistencia | Room (progreso y caché), DataStore (ajustes) |
| Imágenes | Coil |
| DI | Hilt o Koin |
| Build | Gradle (wrapper) compilado en GitHub Actions; sin Android Studio |

## 2. Equivalencias Apple -> Android
| Función en Dusk | Equivalente Android TV |
|---|---|
| SwiftUI / foco tvOS | Compose for TV, `FocusRequester`, `TvLazyRow/Column`, animación de escala/brillo al enfocar |
| VLCKit (TVVLCKit) | libVLC |
| Picture in Picture | PiP de Android (`enterPictureInPictureMode`) |
| SharePlay | Watch Together de Plex o sesión sincronizada propia (mismo UX: invitar, reproducción compartida) |
| Siri (búsqueda) | Asistente de Google / `SearchableInfo` + canales de Android TV |
| Top Shelf | Canales "Play Next" / "Watch Next" de la pantalla de inicio de Android TV |
| Escalado con IA | Shaders GPU en libVLC/GL (p. ej. estilo Anime4K), con interruptor igual al de Dusk |
| Dolby Vision (conversión P7 con libdovi) | Revisar si el decodificador del TCL lo soporta nativo; si no, libdovi vía NDK/JNI |
| TrueHD (VLCKit propio) | libVLC estándar; si falla, transcodificación por Plex (como hace Dusk sin el build propio) |
| Live TV, multi-servidor, Plex Home, Seerr, saltar intro/créditos, "¿sigues viendo?" | Lógica de API + UI; portado directo |

## 3. Compilación con GitHub Actions (sin Android Studio)
- Workflow `.github/workflows/build.yml`: `ubuntu-latest`, `actions/setup-java` (JDK 17), `android-actions/setup-android` y `./gradlew assembleDebug`; sube el APK como artefacto (`actions/upload-artifact`).
- Se dispara en cada `push`; la release firmada va aparte (keystore en Secrets) cuando haga falta.
- Bucle de trabajo: Claude Code edita -> `git push` -> Actions compila -> si falla, leer el log con `gh run view --log-failed` y corregir -> si pasa, bajar el APK con `gh run download`.
- Cachear Gradle (`gradle/actions/setup-gradle`) para que cada build tarde pocos minutos.
- Instalar en el TCL sin Android Studio: `adb install` con las platform-tools (descarga pequeña, sin IDE), o la app Downloader/USB para sideload directo.
- Como no hay emulador ni depurador, cada fase es pequeña y se prueba en el TV; usar logs (`adb logcat`) y pantallas de diagnóstico en modo debug.

## 4. Fases
El detalle (fases, tandas entregadas y decisiones de fidelidad) está en `docs/INVENTARIO_DUSK.md`. Resumen:

1. Tema y login con PIN.
2. Servidores de Plex y selector de perfil de Plex Home.
3. Shell de pestañas y Home (héroe y filas).
4. Bibliotecas, detalle de película/serie/episodio y búsqueda.
5. Reproductor (la versión tvOS no tiene descargas; no se portan).
6. Ajustes completos, Live TV, Playlists, Fotos, Seerr, Watchlist y Watch Next.

## 5. Método de fidelidad
- Una pantalla SwiftUI = una pantalla Compose, con el mismo árbol de componentes cuando se pueda.
- Mantener textos, orden de elementos, iconos (SF Symbols -> equivalentes Material/Vector) y animaciones.
- Cada pantalla termina con una captura en el TCL comparada con la de `screenshots/`.
- El diseño de tvOS 26 (efectos tipo cristal) se aproxima con blur/translucidez; documentar las diferencias inevitables.

## 6. Riesgos
- Conversión de Dolby Vision P7 y TrueHD: dependen del hardware del TCL.
- Escalado con IA: no será idéntico al de Apple.
- El motor de foco de Compose for TV difiere del de tvOS: ajustar a mano.
- Sin emulador local, los errores de UI solo se ven tras compilar e instalar en el TV: ciclos más lentos, por eso las fases son pequeñas.
- No había acceso al código fuente al preparar este plan; todo lo anterior sale del README y de la descripción pública del proyecto. Validar contra `Dusk/` en el paso 0.
