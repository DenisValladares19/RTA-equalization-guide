# 1. Conceptos básicos

## dBFS frente a dB SPL
- **dBFS** (decibelios respecto a la escala completa): nivel digital dentro del teléfono.
  0 dBFS es el máximo antes de saturar. Es un nivel **relativo**: sirve para comparar bandas,
  no para saber "cuánto suena".
- **dB SPL** (nivel de presión sonora): lo que mide un sonómetro. La app lo muestra cuando
  calibras con un calibrador de 94 dB o introduces un desplazamiento manual.
- Para ecualizar **no hace falta SPL**: lo que importa es la *forma* del espectro.

## Octavas y bandas
Una octava es el doble de frecuencia (100 → 200 Hz). El oído percibe las frecuencias de
forma logarítmica, por eso el RTA agrupa el espectro en **bandas de fracción de octava**:
- 1/1 de octava: 10 bandas (visión general).
- **1/3 de octava: 31 bandas de 20 Hz a 20 kHz**. Es la referencia profesional y coincide
  con los ecualizadores gráficos de 31 bandas.
- 1/6 y 1/12: más detalle, útil para localizar resonancias estrechas.

## Ruido rosa y ruido blanco
- **Ruido blanco**: misma energía por cada hercio. En bandas de octava sube 3 dB por octava
  (los agudos parecen enormes).
- **Ruido rosa**: misma energía por octava. **En un RTA de 1/3 de octava se ve plano.**
  Por eso se ecualiza con ruido rosa: cualquier desviación de lo plano es "culpa" del sistema
  o de la sala.

## Vista de barras y línea FFT
- Las **barras** son las bandas de octava: lo que usas para ecualizar.
- La **línea FFT** es el espectro fino (cientos de puntos). La app la normaliza para que el
  ruido rosa también se vea plano y coincida con las barras.

## Promedio
- *Rápido* (125 ms): sigue la música, nervioso.
- *Lento* (1 s): lo normal para ecualizar con ruido rosa.
- *Infinito*: acumula hasta que reinicias. La lectura más estable: déjalo 10–20 s.
