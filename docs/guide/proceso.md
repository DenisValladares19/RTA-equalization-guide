# 3. Proceso paso a paso

1. **Pon el EQ del sistema en plano** (todos los faders a 0 dB).
2. **Elige la curva objetivo** según el uso (ver [curvas](curvas.md)). Su *silueta* aparece
   sombreada detrás de las barras, con la banda de tolerancia (±3 dB por defecto).
3. **Reproduce ruido rosa** y mide con promedio *Lento* o *Infinito*.
4. **Mide en varias posiciones** y activa el *promedio espacial*.
5. **Lee los colores**: verde = dentro de la tolerancia, ámbar = fuera por poco, rojo = fuera por mucho.
   Toca una banda para ver el valor exacto y cuánto subir o bajar.
6. **Ve a la pestaña EQ**:
   - *EQ gráfico*: el valor de cada fader (31, 15 o 10 bandas).
   - *EQ paramétrico*: unos pocos filtros (frecuencia, ganancia, Q) que hacen lo mismo con menos movimientos.
7. **Aplica primero los recortes** grandes (picos de sala, resonancias). Luego, solo si hace
   falta, realces pequeños (la app los limita a +3 dB por defecto).
8. Activa la **Vista previa EQ** para ver la línea violeta: cómo quedaría con la EQ sugerida.
9. **Guarda la medición** (pestaña Sesiones) como "antes".
10. Aplica la EQ, **reinicia el promedio y vuelve a medir**. Usa *Comparar* con la sesión "antes".
11. **Escucha** con música que conozcas. La curva es una guía; el oído decide al final.

## Cómo calcula la app las sugerencias
- Alinea la curva con tu nivel (promedio de 500 Hz a 2 kHz por defecto) para comparar la
  *forma*, no el volumen.
- Para cada banda: `corrección = objetivo − medido`.
- Aplica reglas profesionales:
  - no corrige fuera del rango útil (por defecto de 40 Hz a 16 kHz);
  - no realza **cancelaciones** (valles estrechos de 6 dB o más bajo sus vecinas);
  - limita realces (+3 dB) y recortes (−12 dB);
  - ignora diferencias menores de 1 dB y redondea a 0,5 dB;
  - suaviza bandas vecinas si mides en 1/6 o 1/12 de octava.
- El EQ paramétrico se obtiene ajustando filtros de campana (RBJ) a esa corrección.
