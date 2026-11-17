# Roadmap — RTA Equalization Guide

> Analizador de espectro en tiempo real (RTA) para Android que, además de mostrar
> el espectro como Spectroid, **dibuja detrás una silueta con la curva objetivo**
> (la "curva ideal") y **te dice cuánta ganancia subir o bajar en cada frecuencia**
> para que un equipo de audio profesional suene como debe.

> **Estado (v0.1.0):** fases 0 a 6 implementadas (salvo los perfiles por modelo de teléfono
> y el arrastre de puntos en el editor); de la fase 7, la detección de realimentación. Ver el detalle en la sección 4. Los motores de análisis y de curvas tienen
> tests automáticos; la app Android se compila en GitHub Actions.

---

## 1. Visión

Cuando ecualizas un sistema (PA, monitores, sala, iglesia, cine, estudio) la
pregunta siempre es: *"¿cuánto le quito o le pongo a cada banda?"*. La app
responde eso en tres capas:

1. **Ver** — el espectro real que capta el micrófono (como Spectroid).
2. **Comparar** — una silueta sombreada detrás del espectro con la curva objetivo
   elegida (plana, sala de Harman, curva X de cine, PA en vivo…) y una banda de
   tolerancia de ±dB.
3. **Corregir** — para cada banda, la diferencia entre lo medido y el objetivo,
   convertida en una sugerencia concreta: *"63 Hz: −4,5 dB"*, lista para pasarla
   a un ecualizador gráfico de 31 bandas o a un ecualizador paramétrico.

```
 dB
 +6 ┤░░░                                   ░ = silueta de la curva objetivo
 +3 ┤░░░░░░░    ╱╲                         ▒ = banda de tolerancia ±3 dB
  0 ┤▒▒▒▒░░░░░░╱▒▒╲▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒     ─ = espectro medido (1/3 de octava)
 −3 ┤▒▒▒▒▒▒▒▒▒╱▒▒▒▒╲▒▒▒▒▒▒╱╲▒▒▒▒▒▒░░░░
 −6 ┤──────╱───────────────╱──╲────────░░░
    └┬────┬─────┬─────┬─────┬─────┬─────┬──
     20   63   250    1k    4k   10k   20k  Hz
          ▲                            
          └─ sugerencia: 125 Hz −4 dB (pico de sala)
```

---

## 2. Qué nos enseñó el análisis de Spectroid (con jadx + REA)

Spectroid 1.1.2 (`org.intoorbit.spectrum`, firma original de Carl Reinke,
solo permiso `RECORD_AUDIO`) sirve como **referencia de arquitectura**:

| Pieza | Cómo lo hace Spectroid | Qué tomamos para nuestra app |
|---|---|---|
| Captura | `AudioRecord` mono, PCM 16 bits, frecuencia nativa (≈48 kHz), bloques de 10 ms | Igual, pero con fuente **`UNPROCESSED`** por defecto (sin control automático de ganancia ni supresión de ruido) |
| DC | Paso-alto suave (resta la media móvil) | Igual |
| FFT | **Multirresolución**: FFT de 512 + 5 diezmados (filtro IIR de 5º orden y ÷2 en cada nivel) → ~94 Hz/bin en agudos y ~2,9 Hz/bin en graves | Misma idea: buena resolución en graves sin una FFT gigante |
| Ventana | Blackman-Harris de 4 términos (por defecto) | Igual |
| Escala | dBFS (0 dB = escala completa de 16 bits) | dBFS + **SPL calibrado** opcional |
| Suavizado | Media exponencial (0,30) cada 20 ms | Velocidades Rápida / Lenta / Infinita (promedio acumulado) |
| Visualización | Bins de FFT, eje logarítmico, retención de máximos, picos, cascada | + **bandas de 1/3 y 1/6 de octava**, silueta objetivo y sugerencias de EQ |

**Lo que le falta a Spectroid y es justo nuestro valor añadido:**
- No agrupa en bandas de octava → el ruido rosa no se ve plano (cae 3 dB/octava en vista FFT).
- No hay curva objetivo, ni tolerancias, ni sugerencias de EQ.
- No genera ruido rosa por la salida (sus señales de prueba son internas).
- No hay calibración de micrófono, ni ponderación A/C, ni SPL.

> ⚖️ **Nota legal:** Spectroid no es de código abierto. **No copiamos su código
> decompilado**; reimplementamos las técnicas (FFT multirresolución, ventanas
> estándar), que son algoritmos públicos. La única librería de terceros que usa es
> JTransforms (licencia BSD), que sí podemos usar.

---

## 3. Decisiones técnicas

| Tema | Decisión | Por qué |
|---|---|---|
| Plataforma | **Android nativo en Kotlin** | Acceso a `AudioSource.UNPROCESSED`, micrófonos USB y baja latencia; el navegador aplica procesamiento al micro que falsea la medición |
| Interfaz | Jetpack Compose + `Canvas` propio para el gráfico | 60 fps, control total del dibujo de la silueta |
| DSP | Kotlin puro (o JTransforms) en un hilo dedicado; C++ con NDK solo si el rendimiento lo exige | Simple primero |
| Motor de curvas | Módulo independiente, sin dependencias de Android, con pruebas unitarias | Las curvas y el cálculo de EQ son el corazón: deben poder probarse en cualquier equipo |
| Formatos | Curvas y calibración en JSON / TXT (compatible con el formato de miniDSP UMIK-1) | Interoperabilidad con REW y otras herramientas |

Estructura propuesta del repositorio:

```
app/            → app Android (Compose): captura de audio, gráfico, pantallas, exportación
dsp/            → FFT multirresolución, ventanas, bandas, promedios, ruido rosa, calibración, SPL (Kotlin puro, con tests)
targets/        → curvas objetivo, sugerencias de EQ, EQ gráfico/paramétrico, sesiones (Kotlin puro, con tests)
docs/guide/     → la guía de ecualización (el "manual de uso" profesional)
```

---

## 4. Fases

### Fase 0 — Cimientos (1 semana)
- [x] Crear el proyecto Android (Kotlin, Compose, minSdk 24) con módulos `dsp`, `targets` y `app`.
- [x] Integración continua: compilación + tests en GitHub Actions.
- [x] Documento `docs/research/spectroid.md` con los hallazgos del análisis.
- [x] Definir el formato JSON de las curvas objetivo (ver §5).

**Listo cuando:** la app compila en CI y abre una pantalla vacía.

### Fase 1 — Analizador base, al nivel de Spectroid (2–3 semanas)
- [x] Captura con `AudioRecord` (fuente `UNPROCESSED`, con respaldo a `VOICE_RECOGNITION`, que en Android no lleva control automático de ganancia), selección de frecuencia de muestreo.
- [x] FFT multirresolución (2048 puntos por defecto + 5 diezmados, configurable), ventanas Hann / Hamming / Blackman / Blackman-Harris / Flat-top / rectangular.
- [x] Escala en dBFS, eje logarítmico de 20 Hz a 20 kHz, cuadrícula de dB.
- [x] Promedios: Rápido (≈125 ms), Lento (≈1 s), Infinito (acumulado hasta reiniciar).
- [x] Retención de máximos y botón de congelar.
- [x] Tests: un tono de 1 kHz a escala completa lee 0 dBFS (±0,3 dB) en su banda; tonos de 25 Hz a 16 kHz leen su nivel (±0,5 dB), incluido el paso por los diezmados.

**Listo cuando:** muestra el espectro en vivo con una precisión comparable a Spectroid.

### Fase 2 — Vista RTA profesional (2 semanas)
- [x] Agrupar bins en **bandas de 1/1, 1/3, 1/6 y 1/12 de octava** (frecuencias centrales ISO 266: 20, 25, 31,5, 40 … 20 000 Hz).
- [x] Vista de barras (RTA clásico) y vista de línea suavizada.
- [x] **Generador de ruido rosa** por la salida del teléfono (o al conectarlo a la mesa por cable), más exportación de un archivo WAV de ruido rosa para reproducirlo desde la consola.
- [x] Test: el ruido rosa generado se ve **plano** en la vista de 1/3 de octava (±1,5 dB entre 31,5 Hz y 16 kHz con 30 s de promedio).

**Listo cuando:** al reproducir ruido rosa por un sistema plano, la pantalla se ve plana.

### Fase 3 — La silueta: curvas objetivo (2–3 semanas) ⭐
- [x] Biblioteca de curvas incluidas (ver §5).
- [x] Dibujar la curva **detrás** del espectro como silueta sombreada, más la banda de tolerancia (±1,5 / ±3 / ±6 dB a elegir).
- [x] **Alineación de nivel:** la curva se "ancla" automáticamente al promedio medido entre 500 Hz y 2 kHz (o a una frecuencia que elijas), para comparar forma y no volumen.
- [x] Selector rápido de curva y vista comparativa (cambiar de curva y ver el espectro sobre cada una).
- [x] Colorear cada banda: verde = dentro de la tolerancia, ámbar = fuera por poco, rojo = fuera por mucho.

**Listo cuando:** se puede elegir "PA en vivo" y ver al instante qué bandas sobran o faltan.

### Fase 4 — Guía de ecualización: cuánto subir o bajar (3 semanas) ⭐⭐
- [x] Cálculo por banda: `corrección = objetivo − medido` (sobre el promedio lento o infinito, nunca sobre el instantáneo).
- [x] **Reglas profesionales** aplicadas a la sugerencia:
  - Preferir **recortes** sobre realces; limitar los realces (por defecto a +3 dB, configurable).
  - **No corregir valles estrechos y profundos** (cancelaciones de sala): se marcan como "problema acústico, no de EQ".
  - Ignorar bandas por debajo del límite del sistema (por ejemplo, < 40 Hz si no hay subwoofer) y por encima del rango fiable del micrófono.
  - Suavizar entre bandas vecinas para evitar "dientes de sierra".
- [x] **Salida para ecualizador gráfico de 31 bandas:** tabla `frecuencia → ganancia` redondeada a 0,5 dB.
- [x] **Salida para ecualizador paramétrico:** ajuste automático de 3 a 8 filtros (frecuencia, ganancia, Q) que aproximan la corrección.
- [x] **Vista previa:** la línea "medido + EQ sugerida" superpuesta, para ver cómo quedaría.
- [x] Tests unitarios del motor con casos conocidos (pico de sala de +6 dB en 125 Hz → sugiere un recorte de ≈−6 dB con Q de ≈4).

**Listo cuando:** en una medición real la app produce una tabla de 31 bandas que, aplicada, deja el espectro dentro de ±3 dB de la curva.

### Fase 5 — Medición fiable (2–3 semanas)
- [x] **Archivos de calibración de micrófono** (formato UMIK-1 / Dayton / REW: `Hz  dB`).
- [ ] Perfiles de calibración por modelo de teléfono (requiere medir cada modelo contra un micrófono de referencia).
- [x] Soporte para **micrófono de medición USB** (UMIK-1, Dayton iMM-6 con adaptador).
- [x] **SPL calibrado** (calibrador de 94 dB o referencia manual) y ponderaciones Z / A / C.
- [x] **Promedio espacial:** medir en 3–8 posiciones del público y promediarlas (lo que hacen los ingenieros reales).
- [x] Avisos de calidad: señal saturada, nivel muy bajo, ruido de fondo alto.

**Listo cuando:** con un micro calibrado, el SPL coincide con un sonómetro de referencia (±1 dB).

### Fase 6 — Flujo de trabajo y guardado (2 semanas)
- [x] Guardar sesiones (medición + curva + sugerencias + notas del lugar).
- [x] Comparación **antes / después** superpuesta.
- [x] **Editor de curvas propias:** editar los puntos numéricamente (con vista previa) o copiar la curva de una medición de referencia ("quiero que suene como este sistema").
- [ ] Editar la curva arrastrando los puntos con el dedo sobre el gráfico.
- [x] Exportar en CSV, PNG del gráfico e informe PDF para el cliente o el técnico.

### Fase 7 — Avanzado (futuro)
- [ ] Medición de **función de transferencia** de dos canales (referencia + micrófono) con interfaz USB: fase, coherencia y retardo, como Smaart / REW.
- [x] Detección de **realimentación (feedback)** con sugerencia de filtro de muesca.
- [ ] Alineación de retardo entre el sistema principal y los subwoofers o torres de retardo.
- [ ] Control remoto de procesadores o mesas digitales (por ejemplo, OSC) para aplicar la EQ directamente.

---

## 5. Curvas objetivo incluidas (primera versión)

Cada curva se define como puntos `(Hz, dB)` relativos a 1 kHz, interpolados en
escala logarítmica, más una tolerancia por defecto. Todas se miden con **ruido rosa**
en vista de 1/3 de octava.

| Curva | Uso | Forma (aproximada) |
|---|---|---|
| **Plana (referencia)** | Verificar el sistema y el micro | 0 dB de 20 Hz a 20 kHz |
| **Monitores de estudio / sala** | Mezcla, campo cercano | Plana con caída suave de ≈−1 dB/octava por encima de 1 kHz |
| **Sala doméstica tipo Harman** | Hi-fi, cine en casa | +6 dB de realce en graves por debajo de ≈100 Hz, pendiente de ≈−1 dB/octava hacia los agudos |
| **Curva X de cine (SMPTE ST 202)** | Salas de cine grandes | Plana hasta 2 kHz, después −3 dB/octava (≈−9 dB en 16 kHz) |
| **PA en vivo / concierto** | Rock, pop, música en directo | +8 a +10 dB en subgraves (40–80 Hz), plana en medios, −3 a −6 dB en 10–16 kHz |
| **Club / electrónica** | Discotecas, EDM | Subgraves aún más marcados (+10 a +12 dB en 40–60 Hz), medios planos, agudos con caída suave |
| **Voz / iglesia / conferencias** | Inteligibilidad de la palabra | Graves reducidos por debajo de 100 Hz, ligero realce de presencia (2–4 kHz), caída por encima de 8 kHz |

> Los valores exactos se ajustarán y documentarán en `docs/guide/curvas.md` con
> sus fuentes (SMPTE, AES, la investigación de Harman y la práctica de ingenieros de directo),
> y serán editables por el usuario.

Formato propuesto (`targets/curves/pa-en-vivo.json`):

```json
{
  "id": "pa-en-vivo",
  "nombre": "PA en vivo / concierto",
  "referencia_hz": 1000,
  "tolerancia_db": 3,
  "puntos": [[20, 6], [40, 9], [80, 8], [150, 3], [300, 0],
             [1000, 0], [4000, -1], [10000, -4], [16000, -6], [20000, -9]]
}
```

---

## 6. La guía escrita (`docs/guide/`)

El repositorio también será una guía para ecualizar, enlazada desde la app:

1. Conceptos: dBFS frente a dB SPL, octavas, ruido rosa y ruido blanco, por qué se mide en 1/3 de octava.
2. Preparación: dónde colocar el micrófono, cuántas posiciones medir, a qué nivel reproducir el ruido rosa.
3. Proceso paso a paso: medir → elegir la curva → recortar picos → realces mínimos → verificar → **escuchar**.
4. Qué **no** se arregla con EQ: cancelaciones de sala, reflexiones, problemas de fase entre altavoces.
5. Fichas por escenario: concierto, iglesia, club, cine, estudio.

---

## 7. Riesgos y limitaciones

| Riesgo | Mitigación |
|---|---|
| Los micrófonos de teléfono no son fiables por debajo de ≈50 Hz y por encima de ≈12 kHz | Avisos en pantalla, perfiles de calibración y soporte para micrófono USB |
| Android aplica procesamiento al micrófono | Usar `UNPROCESSED`, detectar si el teléfono no lo soporta y avisar |
| Realces excesivos dañan altavoces o provocan realimentación | Límite de realce, preferencia por recortes y advertencias |
| La "curva ideal" depende del gusto y del género | Varias curvas, tolerancias, editor de curvas propias y la escucha como juez final |
| Una sola posición de micrófono engaña | Promedio espacial (Fase 5) y recomendación en la guía |

---

## 8. Resumen de prioridades

| Prioridad | Fases | Resultado |
|---|---|---|
| **MVP** | 0 → 4 | Analizador + silueta de la curva objetivo + tabla de cuánto subir o bajar por banda |
| **Profesional** | 5 → 6 | Mediciones calibradas, promedio espacial, sesiones e informes |
| **Avanzado** | 7 | Función de transferencia, feedback y control remoto del equipo |

Duración estimada del MVP: **unas 10–12 semanas** para una persona a tiempo parcial.
