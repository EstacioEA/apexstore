# Bitácora del agente

## 2026-10-06 — T-00/T-01

- `slice2java -v` funcionó y reportó 3.7.11.
- `java -version` del PATH reportó Java 8; la inspección de `JAVA_HOME` encontró Amazon Corretto JDK 21. Se decidió usar explícitamente ese JDK.
- `gradle -v` y `mvn -v` no están disponibles y no existe wrapper en el repositorio inicial. La preparación del build queda pendiente de resolver con una instalación local o distribución descargada.
- El primer `clean build` falló porque el toolchain pedía exactamente JDK 17 y Gradle no lo encontró, aunque sí está instalado JDK 21. Se corrigió el toolchain a Java 21, manteniendo el requisito mínimo Java 17+.
- El segundo `clean build` falló porque `slice2java` rechaza operaciones que solo difieren en mayúsculas del nombre de la interfaz (`eco`/`Eco`). Se renombró la operación del Slice de humo a `repetir`.
