# Decisiones

| ID | Contexto | Decisión | Alternativa descartada | Impacto |
|---|---|---|---|---|
| D-01 | `slice2java -v` reporta 3.7.11 y `java` del PATH reporta Java 8 | Usar el JDK 21 de `JAVA_HOME` para satisfacer Java 17+ y fijar ICE 3.7.11 | Usar Java 8 del PATH | El README deberá explicar la configuración de `JAVA_HOME` |
| D-02 | ICE 3.7.11 rechaza operaciones que solo difieren en mayúsculas del nombre de la interfaz (`confirmarCompra`/`ConfirmarCompra`, `iniciarPagoOrden`/`IniciarPagoOrden`, `notificarResultadoPago`/`NotificarResultadoPago`) y rechaza el método `estadoCircuitos` junto al diccionario homónimo | Conservar módulos e interfaces Slice y renombrar únicamente las operaciones a `confirmar`, `iniciar`, `notificar` y `obtenerEstadoCircuitos`, manteniendo exactamente la semántica | Forzar `--compat` o conservar el contrato inválido | Los servants y clientes usarán los nombres compilables; la desviación debe quedar trazada en README/informe |
