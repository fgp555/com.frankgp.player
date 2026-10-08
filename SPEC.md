# SPEC.md - FGP Player (Especificación del Proyecto)

## 1. Visión General
**FGP Player** (`com.frankgp.player`) es una aplicación nativa para Android desarrollada en Kotlin y Jetpack Compose, enfocada en la reproducción de video fluida, moderna, eficiente y con soporte para múltiples formatos, transmisiones locales y streaming/descarga desde URLs web.

---

## 2. Arquitectura y Stack Tecnológico
- **Lenguaje:** Kotlin (2.2.x)
- **UI Toolkit:** Jetpack Compose + Material 3 (Edge-to-Edge) con `material-icons-extended`
- **Carga de Imágenes y Miniaturas:** Coil Compose & Coil Video (`io.coil-kt:coil-compose`, `io.coil-kt:coil-video`) v2.6.0
- **Motor de Video:** AndroidX Media3 ExoPlayer (`androidx.media3:media3-exoplayer`, `androidx.media3:media3-ui`, `androidx.media3:media3-session`) v1.5.1
- **Cliente HTTP / Descargas:** OkHttp3 / Android `DownloadManager` + Coroutines
- **Arquitectura:** MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF)
- **Asincronía & Estado:** Kotlin Coroutines + `StateFlow` / Compose `State`

---

## 3. Características Implementadas y Verificadas

### 3.1. Detección y Escaneo Automático de Medios Locales
- **Carga Automática al Iniciar:** Escaneo e inicialización automática al abrir la app con solicitud de permisos (`READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE`).
- **Sincronización Asíncrona del Reproductor (UX Fix):** Sincronización garantizada entre la carga de la lista y la conexión asíncrona del `MediaController`, permitiendo reproducir inmediatamente cualquier video de la lista al iniciar la app sin requerir presionar "Escanear".
- **Escaneo Dual (File System + MediaStore):**
  - Inspección directa de carpetas predeterminadas (`Download`, `Movies`/`Videos`, `DCIM`).
  - Consulta en `MediaStore.Files` para detectar formatos de video con tipos MIME o extensiones variadas (`.mp4`, `.mkv`, `.webm`, `.avi`, `.mov`, `.3gp`, `.ts`).
  - Invocación automática de `MediaScannerConnection.scanFile()` para forzar el indexado en Android de archivos recién transferidos o descargados.
- **Apertura de Archivos:** Selector manual vía Storage Access Framework (`GetContent`).

### 3.2. Motor de Video Robusto (AndroidX Media3 ExoPlayer)
- **Extracción Adaptativa:** Uso de `DefaultExtractorsFactory` con búsqueda continua para soportar archivos creados con `yt-dlp` u HLS en contenedores no estándar.
- **Decodificación de Respaldo:** Activación de `setEnableDecoderFallback(true)` para cambiar a decodificación por software si el hardware falla en resoluciones HD / 1080p.
- **Superficie Estable en Compose:** Manejo optimizado de `AndroidView(PlayerView)` evitando la reconexión repetida de la superficie de renderizado y garantizando la fluidez de video.

### 3.3. Interfaz de Usuario (UI/UX Minimalista con Material 3)
- **Barra de Iconos Compactos:**
  - `Refresh`: Escaneo/refresco manual de carpetas.
  - `FolderOpen`: Selector de archivos individuales.
  - `Link`: Abrir diálogo para ingresar URL web/stream (YouTube, TikTok, HLS, MP4).
  - `PictureInPictureAlt`: Activación de modo PiP.
  - `Fullscreen`: Modo Pantalla Completa inmersivo.
  - `PlayArrow` / `Pause`: Botón dinámico de reproducción/pausa vinculado reactivamente a `StateFlow<Boolean>` de ExoPlayer (`onIsPlayingChanged`).
  - `Lock` / `LockOpen`: Botón de bloqueo de controles sobre el video (colocado a la derecha del botón Play/Pausa). Desactiva los controles superpuestos del video para evitar apariciones accidental durante la visualización.
- **Gestos de Pantalla Completa:**
  - Gesto **Swipe Up (Deslizar hacia arriba)** sobre el video para entrar a Pantalla Completa.
  - Gesto **Swipe Down (Deslizar hacia abajo)** o botón flotante para salir de Pantalla Completa.
  - Ocultación inmersiva de barras del sistema (`WindowInsetsControllerCompat`).
- **Lista de Reproducción (Playlist) con Miniaturas de Video:**
  - Muestra una **miniaturización/preview** de fotograma de video a la izquierda de cada tarjeta mediante `CoilVideo`.
  - Resaltado visual del video en reproducción actual.

### 3.4. Servicios de Sistema, Notificaciones y PiP
- **Reproducción en Segundo Plano:** Implementada con `PlaybackService` (`MediaSessionService`) para mantener la reproducción activa al minimizar la app.
- **Metadatos y Miniatura en Notificación de Sistema:** Integración de `MediaMetadata.Builder` (`setTitle`, `setDisplayTitle`, `setArtworkUri`) en cada `MediaItem` de ExoPlayer para proyectar el **nombre real del video** y la **miniatura/artwork** directamente en la tarjeta de notificación multimedia de Android y pantalla de bloqueo.
- **Picture-in-Picture (PiP):** Activación manual vía botón y automática mediante `onUserLeaveHint` cuando se reproduce un video.

### 3.5. Reproducción por URL y Descarga Offline
- **Entrada de Enlace:** Diálogo interactivo para pegar URLs de video de la web (HLS, MP4 directos, YouTube/TikTok).
- **Streaming Online Inmediato:** Reproducción del flujo de red directamente en ExoPlayer.
- **Descargador Local para Modo Offline:**
  - Validación de cabeceras HTTP con OkHttp y configuración de `User-Agent` de navegador.
  - Invocación a `DownloadManager` de Android que guarda el video en la carpeta pública `/sdcard/Download/`.
  - Invocación automática a `MediaScannerConnection` para que el video descargado aparezca inmediatamente en la lista local para reproducción offline sin conexión a internet.

### 3.6. Metadatos e Información Detallada de Archivos
- **Atributos Completos en VideoItem:** Título, Ruta física (`Path`), Tamaño en Bytes/MB, Duración (`MM:SS` / `HH:MM:SS`) y URI.
- **Icono de Información ℹ️:** Botón de información (`Icons.Default.Info`) integrado en cada elemento de la lista de reproducción.
- **Diálogo Modal de Detalles (`VideoInfoDialog`):** Muestra el resumen completo del archivo (Ruta de ubicación en el celular, peso formateado en MB/KB y duración exacta).
- **Resumen Directo en la Tarjeta de Playlist (`PlaylistItemCard`):** Muestra directamente bajo el nombre del video en la lista:
  - **Carpeta contenedora** (ej. `📁 Download`, `📁 Movies`, `📁 DCIM`).
  - **Duración formateada** (ej. `⏱ 01:10` / `00:12`).
  - **Peso/Tamaño formateado** (ej. `💾 5.45 MB` / `680 KB`).

### 3.7. Icono Adaptativo del Lanzador (App Icon)
- **Diseño Vectorial Adaptativo:**
  - Fondo elegante en tono azul noche en [ic_launcher_background.xml](file:///E:/dev/kotlin/com.frankgp.player/app/src/main/res/drawable/ic_launcher_background.xml) (`#0F172A`).
  - Primer plano con símbolo de reproducción (*Play*) en blanco puro rodeado por un anillo luminoso en azul eléctrico (`#38BDF8`) en [ic_launcher_foreground.xml](file:///E:/dev/kotlin/com.frankgp.player/app/src/main/res/drawable/ic_launcher_foreground.xml).

---

## 4. Requisitos del Sistema y Compilación
- **Min SDK:** 24 (Android 7.0)
- **Target SDK / Compile SDK:** 37
- **Java Compatibility:** Java 11 / Kotlin DSL
- **Verificación de Compilación:** `./gradlew assembleDebug` verificado.
