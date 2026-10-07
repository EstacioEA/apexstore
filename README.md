# ApexStore ICE

Implementación Java + ZeroC ICE del flujo de pagos simulado de ApexStore.

## Requisitos

- Windows, Linux o macOS.
- JDK 17 o superior. El entorno validado usa Amazon Corretto 21.
- `slice2java` 3.7.11 en el `PATH`.
- Gradle Wrapper (`gradlew`/`gradlew.bat`).

La dependencia ICE debe conservar el mayor/menor de `slice2java`; está fijada en `gradle.properties`.

## Compilar y probar

```powershell
$env:JAVA_HOME="C:\Program Files\Amazon Corretto\jdk21.0.8_9"
.\gradlew.bat clean build
```

El contrato Slice se genera automáticamente desde `src/main/slice/apexstore.ice`.

## Ejecutar

En cuatro terminales:

```powershell
.\gradlew.bat runDb
.\gradlew.bat runPasarelas
.\gradlew.bat runBackend
.\gradlew.bat runCliente -Pscenario=smoke
```

O en una JVM con communicators independientes:

```powershell
.\gradlew.bat runTodo
```

Escenarios actualmente ejecutables por el cliente: `smoke`, `idempotencia` y `extension`.
La salida real se guarda en `docs/evidencia/`.

## Troubleshooting

- Si `slice2java` no se encuentra, instalar ZeroC ICE 3.7.11 y agregar su directorio `bin` al `PATH`.
- Si aparece un error de versión, alinear `iceVersion` con `slice2java -v`.
- Si un puerto está ocupado, detener el proceso anterior o cambiar los endpoints de `config/*.properties`.
- Las pasarelas no realizan llamadas externas: toda latencia, rechazo y callback es memoria local.

## Desviaciones conocidas

ICE 3.7.11 no acepta operaciones que solo difieren en mayúsculas del nombre de una interfaz; por ello algunas
operaciones del contrato se renombraron mínimamente. La decisión está en `docs/DECISIONES.md`.
