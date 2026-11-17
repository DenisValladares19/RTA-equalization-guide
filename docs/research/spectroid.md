# Análisis de Spectroid 1.1.2 (referencia de arquitectura)

APK `org.intoorbit.spectrum` 1.1.2 (versionCode 31), analizado de forma estática con
**jadx 1.5.1** y **REA 6.9.0** (motor jadx-headless-mcp 0.7.1).

## Identidad y permisos
- Firmado por el autor original (Carl Reinke, intoorbit.org; certificado de 2014),
  esquemas v1 y v2; el resumen v2 coincide con el APK (no fue modificado tras firmarse).
- Único permiso: `RECORD_AUDIO`. Sin internet, sin servicios en segundo plano.
- 38 clases; única librería de terceros: JTransforms (BSD).

## Procesado de audio
| Etapa | Implementación |
|---|---|
| Captura | `AudioRecord` mono, PCM 16 bits, frecuencia nativa; bloques de 10 ms |
| DC | Resta de la media móvil (paso-alto suave), activado por defecto |
| FFT | Multirresolución: FFT de 512 + 5 diezmados (IIR de 5º orden + ÷2 por nivel) |
| Ventana | Blackman-Harris de 4 términos por defecto; además Hann, Hamming, Blackman, flat-top, coseno, rectangular, triangular y Welch |
| Escala | dBFS: potencia × 4/N², 10·log10 respecto a 2^30 |
| Suavizado | Media exponencial (0,30 de peso al cuadro anterior) cada 20 ms |
| Pantalla | Eje logarítmico, retención de máximos, marcadores de picos, cascada |

## Lo que le falta (y añade este proyecto)
- Bandas de fracción de octava (el ruido rosa no se ve plano en su vista FFT).
- Curvas objetivo, tolerancias y sugerencias de EQ.
- Generador de ruido rosa por la salida (sus señales de prueba son internas).
- Calibración de micrófono, SPL y ponderación A/C.

## Nota legal
Spectroid no es de código abierto: **este proyecto no copia su código**. Se reimplementan desde
cero técnicas públicas (FFT multirresolución por diezmado, ventanas estándar). La FFT y el filtro
de diezmado de este proyecto son propios (FIR de fase lineal en lugar de IIR).
