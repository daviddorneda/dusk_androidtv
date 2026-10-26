# Dusk for Android TV

Port no oficial a Android TV (Kotlin + Jetpack Compose for TV) de [Dusk for Plex](https://github.com/marvinvr/dusk-player), cliente de Plex para Apple TV de Marvin Vr.

Estado: esqueleto del proyecto. Plan de trabajo en `docs/PORT_DUSK_ANDROIDTV.md`.

## Compilar
Se compila en GitHub Actions: cada `push` a `main` genera un APK debug descargable desde la pestaña Actions (artefacto `dusk-androidtv-debug`).

## Instalar en el TV
```
adb connect IP_DEL_TV:5555
adb install -r app-debug.apk
```

## Créditos y licencias
- Diseño, funcionalidad y estructura de la app original: Dusk for Plex, © sus autores, licencia MIT. Se conserva su aviso de licencia en `NOTICE`.
- Este proyecto no está afiliado a Plex ni a los autores de Dusk.
