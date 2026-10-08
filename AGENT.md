# AGENT.md - Instrucciones y Reglas para Agentes de IA

Este documento define las pautas, convenciones de código y reglas de desarrollo para agentes de IA que trabajen en el proyecto **FGP Player**.

---

## 1. Reglas Generales
- **Prohibición de Build Automático:** **NO ejecutes herramientas de build o compilación de Gradle (`gradle_build`, `assembleDebug`, etc.)**. La compilación, construcción y ejecución en dispositivos se realizará manualmente por el usuario.
- **Tecnologías Estándar:** Usa exclusivamente **AndroidX Media3** para funcionalidades de video/audio. No uses la biblioteca obsoleta `com.google.android.exoplayer2`.
- **Diseño de UI:** Toda la interfaz debe construirse con **Jetpack Compose** y **Material 3**. Evita vistas XML tradicionales salvo casos estrictamente necesarios de interoperabilidad (como `AndroidView` para renderizar componentes de Media3).
- **Gestión de Dependencias:** Registra y gestiona todas las dependencias en `gradle/libs.versions.toml` (Version Catalog). No agregues dependencias directamente con cadenas estáticas en `build.gradle.kts`.

---

## 2. Estructura y Estilo de Código
- **Kotlin Idiomático:** Usa funciones de extensión, corrutinas, y tipos inmutables (`val`) cuando sea posible.
- **Gestión del Ciclo de Vida:**
  - Instancias de `ExoPlayer` deben ser liberadas (`player.release()`) en `ON_STOP` o `ON_DESTROY` / `DisposableEffect` para evitar fugas de memoria y uso innecesario de recursos.
- **Manejo de Estado:**
  - Mantén el estado de la UI centralizado en el `ViewModel` mediante `StateFlow`.
  - Separa la lógica de negocio/reproducción de la capa composable.

---

## 3. Protocolo de Modificación de Archivos y Compilación
- Revisa la sintaxis de los archivos modificados mediante análisis estático en lugar de ejecutar builds.
- No invoques nunca comandos de compilación/build de Gradle de forma automática.
- Sigue la arquitectura propuesta en `SPEC.md`.
- No elimines ni alteres la configuración existente de Gradle sin justificación explícita.
