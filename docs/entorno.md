# Entorno de ejecución

Registro de T-00, ejecutado el 2026-10-06 en Windows 11 Pro 64 bits.

| Herramienta | Resultado real |
|---|---|
| PowerShell | 7.6.6 |
| Sistema operativo | Microsoft Windows 11 Pro 10.0.26200, 64 bits |
| `java -version` | Java 1.8.0_401 (PATH del sistema) |
| `JAVA_HOME/bin/java -version` | OpenJDK 21.0.8 LTS (Amazon Corretto) |
| `gradle -v` | No instalado |
| `mvn -v` | No instalado |
| `slice2java -v` | 3.7.11 |

La compilación usará el JDK 21 disponible en `JAVA_HOME`, compatible con el requisito Java 17+.
La dependencia ICE se fija inicialmente en `3.7.11`, con el mismo mayor/menor que `slice2java`.
Se requiere disponer de Gradle o del wrapper antes de ejecutar el build.
