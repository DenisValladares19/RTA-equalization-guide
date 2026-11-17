# 3. Proceso paso a paso

1. **Pon el EQ del sistema en plano** (todos los faders a 0 dB).
2. **Elige la curva** (la silueta) en el panel del Analizador. La banda verde se dibuja **fija**
   en el nivel de la guía (−25 dBFS por defecto, en bandas de 1/3 de octava).
3. **Reproduce ruido rosa** por el sistema y mide con promedio *Lento* o *Infinito*.
4. **Ajusta el nivel**: sube o baja el volumen del sistema (o mueve la guía con ▲ / ▼, o pulsa
   *Fijar guía al nivel actual*) hasta que los medios (500 Hz–2 kHz) caigan dentro de la banda.
5. **Mide en varias posiciones** y activa el *promedio espacial*.
   La escala va por defecto de 0 a −90 dBFS. **Arrastra el gráfico** arriba/abajo para moverla,
   **pellizca** para ampliarla o reducirla, o pulsa *Autoescala* para encuadrar espectro y guía.
6. **Compara la línea con la banda verde**: donde la línea se sale por arriba, sobra; por debajo, falta.
   Toca el espectro para poner un cursor en la frecuencia exacta (arrastra en horizontal para
   moverlo, doble toque para quitarlo): muestra su nivel, el de la guía y la diferencia.
7. **Ecualiza** en tu mesa o procesador: recorta primero los picos, realza poco.
8. **Guarda la medición** (pestaña Sesiones) como "antes", aplica los cambios, reinicia el promedio,
   vuelve a medir y usa *Comparar* para ver el antes y el después.
9. **Escucha** con música que conozcas. La guía orienta; el oído decide al final.

## Sugerencias automáticas de EQ (ocultas)
El motor de sugerencias sigue en el código, aunque la pestaña está oculta. Funciona así:
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
