# Bitácora del agente

## 2026-10-06 — T-00/T-01

- `slice2java -v` funcionó y reportó 3.7.11.
- `java -version` del PATH reportó Java 8; la inspección de `JAVA_HOME` encontró Amazon Corretto JDK 21. Se decidió usar explícitamente ese JDK.
- `gradle -v` y `mvn -v` no están disponibles y no existe wrapper en el repositorio inicial. La preparación del build queda pendiente de resolver con una instalación local o distribución descargada.
- El primer `clean build` falló porque el toolchain pedía exactamente JDK 17 y Gradle no lo encontró, aunque sí está instalado JDK 21. Se corrigió el toolchain a Java 21, manteniendo el requisito mínimo Java 17+.
- El segundo `clean build` falló porque `slice2java` rechaza operaciones que solo difieren en mayúsculas del nombre de la interfaz (`eco`/`Eco`). Se renombró la operación del Slice de humo a `repetir`.
- T-02 falló inicialmente con el contrato normativo: `slice2java` rechazó tres operaciones por colisión de capitalización con sus interfaces y una operación por colisión con un diccionario. `--compat` produjo los mismos errores. Se aplicó el ajuste mínimo de nombres y se registró como D-02.
- El primer test de T-03 no pudo descubrir pruebas porque JUnit Jupiter 5.12.2 quedó desalineado con el launcher de Gradle (`OutputDirectoryProvider`). Se ajustó a JUnit 5.10.2, la versión indicada en el plan técnico.
- La segunda ejecución conservó JUnit Platform 1.12.2 porque ArchUnit 1.4.1 imponía el BOM 5.12.2. Se ajustó ArchUnit a 1.3.0, también según el plan técnico, para alinear todo el stack JUnit.
- La primera compilación de T-04 asumió erróneamente que Slice generaba clases para `sequence` y `dictionary`. El mapeo Java 3.7 genera arrays y `Map`; se corrigieron las firmas JDBC.
- T-06 inicialmente intentó que una misma clase implementara `EstrategiaPagos` y `AdminPasarela`; Java/ICE generó dos despachadores `_iceDispatch` incompatibles. Se separó el servant administrativo en `AdminPasarelaI` sin duplicar estado.
- T-08 requirió adaptar el acceso a propiedades: `com.zeroc.Ice.Properties` solo expone getters de un argumento. Se añadió un helper de valores por defecto y se corrigieron conversiones de rangos `long` a `int`.
- El primer test concurrente de checkout reveló una ventana de carrera: el índice de idempotencia se publicaba antes que la orden. Se invirtió el orden de publicación (`porId` antes de `porClave`) y se eliminó la orden perdedora.
