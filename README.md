# RTA Guía EQ

Analizador de espectro en tiempo real (RTA) para Android que, además de mostrar el
espectro, **dibuja detrás una silueta con la curva objetivo** y te dice **cuánto subir o
bajar cada frecuencia** para ecualizar un sistema de audio profesional.

- 📈 RTA en 1/1, 1/3, 1/6 o 1/12 de octava con análisis multirresolución (graves finos sin perder velocidad).
- 🎯 Curvas objetivo con banda de tolerancia: plana, estudio, sala tipo Harman, curva X, PA en vivo, club, voz y graves pronunciados (guía de directo). El espectro se dibuja como línea, en escala absoluta, sobre una banda verde fija en el nivel que elijas (−25 dBFS por defecto); también hay vista de barras. Editables y creables desde una medición.
- 👆 Toca el espectro para ver la frecuencia, su nivel y la diferencia con la guía; comparación antes/después con mediciones guardadas.
- 🎚️ Motor de sugerencias de EQ (31/15/10 bandas y paramétrico) incluido en el código; su pestaña está oculta.
- 🎤 Calibración de micrófono (UMIK, Dayton, REW), micrófonos USB, SPL con calibrador de 94 dB, ponderación A/C.
- 📍 Promedio espacial de varias posiciones, ruido de fondo y avisos de saturación.
- 🔊 Generador de ruido rosa y exportación WAV para reproducir desde la mesa.
- 🔔 Detección de realimentación (feedback) con sugerencia de filtro de muesca.
- 💾 Sesiones guardadas y exportación a CSV, PDF e imagen.

📖 **[Guía de ecualización](docs/guide/README.md)** · 🗺️ **[Roadmap](ROADMAP.md)** · 🔬 **[Análisis de Spectroid](docs/research/spectroid.md)**

## Estructura

```
dsp/      Kotlin puro: FFT, análisis multirresolución, bandas, promedios, ruido rosa,
          calibración, SPL, calidad de señal, detección de realimentación
targets/  Kotlin puro: curvas objetivo, alineación, comparación, sugerencias de EQ,
          EQ gráfico/paramétrico, editor de curvas, sesiones y CSV
app/      App Android (Jetpack Compose): captura, gráfico, pantallas, exportación
docs/     Guía de ecualización e investigación
```

## Compilar

Requisitos: JDK 17 o superior. Para la app, además, el Android SDK (Android Studio).

```bash
./gradlew testCore              # tests de los motores (sin Android SDK)
./gradlew :app:assembleDebug    # APK (necesita el Android SDK)
```

El módulo `app` se incluye automáticamente cuando hay un Android SDK (`ANDROID_HOME`,
`ANDROID_SDK_ROOT` o `local.properties` con `sdk.dir`). La integración continua de GitHub
Actions ejecuta los tests y publica el APK como artefacto en cada push.

## Límites conocidos

- Los micrófonos de teléfono no son fiables por debajo de ~50 Hz ni por encima de ~12 kHz:
  para trabajo profesional usa un micrófono de medición calibrado.
- Es un RTA de un canal: no mide fase ni retardo (función de transferencia), que es lo que
  hace falta para alinear subwoofers o torres de retardo. Ver el [roadmap](ROADMAP.md).
- El APK de CI se firma con la clave de depuración; para publicarlo en una tienda, configura tu propia firma.
