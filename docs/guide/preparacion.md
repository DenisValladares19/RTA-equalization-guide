# 2. Preparar la medición

## El micrófono
- **Micrófono del teléfono**: útil para orientarse, pero poco fiable por debajo de ~50 Hz y
  por encima de ~12 kHz, y cada modelo suena distinto.
- **Micrófono de medición USB** (miniDSP UMIK-1/UMIK-2, Dayton iMM-6 con adaptador): lo
  recomendado para trabajo profesional. Carga su **archivo de calibración** en
  *Ajustes → Calibración del micrófono* (lo descargas de la web del fabricante con su número de serie).
- En *Ajustes → Fuente*, deja **Automático**: la app usa audio *sin procesar* si el teléfono
  lo permite (sin control automático de ganancia ni supresión de ruido).

## Dónde colocarlo
- A la altura de la cabeza del público (≈1,2 m sentado, ≈1,7 m de pie), apuntando al sistema.
- Lejos de paredes y del suelo directo (al menos 1 m si es posible).
- **Varias posiciones** (de 3 a 8) repartidas por la zona de público, sin repetir distancias
  simétricas. Usa *Capturar posición* en cada una: la app promedia en potencia (promedio espacial).
  Una sola posición engaña, porque cada punto de una sala tiene sus propias cancelaciones.

## El ruido rosa
- Reprodúcelo **a través del sistema**: conecta el teléfono a la mesa o usa el WAV que exporta
  la app (*Ajustes → Guardar WAV de ruido rosa*) desde la consola.
- Nivel: el de un programa normal (por ejemplo, 80–90 dB SPL en el público). Debe quedar al
  menos **10 dB por encima del ruido de fondo** en todas las bandas.
- Captura el **ruido de fondo** con el sistema en silencio (*Ajustes → Ruido de fondo*): las
  bandas poco fiables se marcan y no se corrigen.
- La app avisa si la señal **satura**: baja el nivel o aleja el micrófono.
