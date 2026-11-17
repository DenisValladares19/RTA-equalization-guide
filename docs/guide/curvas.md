# 4. Curvas objetivo incluidas

Todas se definen como puntos (Hz, dB) relativos a 1 kHz, se interpolan en escala
logarítmica y se comparan con **ruido rosa medido en bandas de 1/3 de octava**.
Son puntos de partida razonables, no leyes: ajústalas a tu gusto en la pestaña *Curvas*
(duplicar, editar puntos o crear una a partir de una medición de un sistema que te guste).

| Curva | Uso | Forma | Tolerancia |
|---|---|---|---|
| Plana | Verificación, referencia | 0 dB de 20 Hz a 20 kHz | ±3 dB |
| Monitores de estudio | Mezcla en campo cercano | Plana hasta 1 kHz y −1 dB/octava después (−4 dB a 16 kHz) | ±2 dB |
| Sala doméstica (tipo Harman) | Hi-fi, cine en casa | +6 dB por debajo de 100 Hz que baja a 0 hacia 500 Hz, pendiente de −1 dB/octava en agudos | ±3 dB |
| Curva X (SMPTE ST 202) | Salas de cine grandes | Plana hasta 2 kHz y −3 dB/octava (−9 dB a 16 kHz) | ±3 dB |
| PA en vivo / concierto | Rock, pop, directo | +9 dB en 40 Hz, +8 en 80, +3 en 150, plana de 300 Hz a 1 kHz, −4 dB a 10 kHz, −6 a 16 kHz | ±3 dB |
| Club / electrónica | Discotecas, EDM | +12 dB en 40 Hz, +11 en 63, +6 en 100, medios planos, −5 dB a 12,5 kHz | ±3 dB |
| Voz / iglesia / conferencias | Inteligibilidad | −12 dB en 20 Hz, −4 en 80, presencia +2,5 dB en 3,15 kHz, −6 dB a 12,5 kHz | ±3 dB |

### Por qué los agudos caen
En una sala, a distancia, el sonido directo pierde agudos y el campo reverberante domina.
Un sistema que mide **plano** en el público suele sonar **brillante y duro**. Las curvas con
caída suave en agudos (−1 a −3 dB/octava) suenan más naturales.

### Por qué los graves suben
El oído es menos sensible a los graves a niveles moderados (curvas de igual sonoridad) y la
música en directo y electrónica se disfruta con más "peso". Las curvas de PA y club llevan
realces en subgraves que deben salir de los **subwoofers**, no de realzar el EQ de los principales.

### Formato del archivo de curva
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

Fuentes y referencias: SMPTE ST 202 (curva X); Olive y Welti, *The relationship between
perception and measurement of headphone and loudspeaker sound quality* (investigación de
Harman sobre la respuesta preferida en sala); Brüel & Kjær, *Relevant loudspeaker tests* (1974,
pendiente de −1 dB/octava); práctica habitual de ingenieros de directo para PA y club.
