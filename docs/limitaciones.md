# Limitaciones

- La carga de 8.000 solicitudes/s no se demuestra en una sola máquina; la implementación actual no incluye todavía
  el generador de 2.000 compras ni los reportes P50/P95/P99 de E2.
- Se usa H2 embebido en modo PostgreSQL, no un servidor PostgreSQL real.
- La comunicación usa TCP local de ICE, no HTTPS/TLS.
- Las órdenes viven en memoria; una caída del backend después del commit de una transacción puede dejar la orden
  desactualizada hasta un nuevo intento del cliente.
- El conflicto tardío se audita y contabiliza, pero no existe reversión automática del cobro simulado.
- `RunAll` inicia los tres nodos de servicio con communicators separados; el cliente se ejecuta como tarea aparte.
- Los escenarios administrativos E3–E6, E8 y E11 aún no tienen comandos JUnit/CLI completos; no se reportan como PASS.
- El contrato requirió el ajuste D-02 por las restricciones de nombres de ICE 3.7.11.
