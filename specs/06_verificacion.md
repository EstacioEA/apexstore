# 06 — Verificación: escenarios, aserciones y evidencia

Cada escenario existe en **dos formas**: (a) subcomando del cliente (`runCliente --args="<nombre>"`) que imprime un
reporte legible para el informe; (b) test JUnit de integración que falla si una aserción no se cumple. Los tests
usan `RunAll` con puertos libres y tiempos reducidos (`config/test.properties`: `Expiracion.TxPendienteMs=3000`,
`Expiracion.PeriodoMs=300`, callbacks cortos). Los valores numéricos de abajo son los por defecto del cliente;
los tests pueden escalarlos, pero no relajar las aserciones cualitativas.

| ID | Nombre | Pasos | Aserciones |
|---|---|---|---|
| E1 | `smoke` | 1 compra por cada medio (STRIPE, PSE, CRIPTO) con atributos válidos; polling cada 100 ms hasta estado terminal (máx. 30 s) | Respuesta inicial `OrdenPendientePago` en < 250 ms; las 3 órdenes terminan `OrdenConfirmada` u `OrdenRechazada` (rechazo aleatorio: forzar `tasaRechazo=0` en el test para determinismo); en BD cada tx tiene ≥ 2 eventos de auditoría (`CREADA`, `TRANSICION`) |
| E2 | `carga` | N=2 000 compras (50 % STRIPE, 30 % PSE, 20 % CRIPTO), 200 hilos, mitad por `CanalWeb` y mitad por `CanalMovil`, claves de idempotencia únicas | `contarPorEstado` suma N; **0** órdenes con más de 1 tx (consulta `GROUP BY orden_id HAVING COUNT>1` vacía); tras drenar, **0** `TxPendiente`; `p95Ms` de `latenciaOrquestacion` **< 250** (reportar valor real) y P95 del cliente reportado; el reporte declara "carga escalada" |
| E3 | `falla-pse` | `AdminPSE.configurar(CAIDA)`; 300 compras mezcladas | STRIPE y CRIPTO: 100 % de sus órdenes siguen su flujo normal y su P95 no se degrada > 2× respecto a la línea base medida en el mismo escenario; PSE: `OrdenFallida` con mensaje de reintento; circuito PSE pasa a `ABIERTO` y, abierto, las respuestas PSE fallan en < 50 ms (fail-fast); al volver a `NORMAL` y esperar `AbiertoMs`, una compra PSE de prueba cierra el circuito (`CERRADO`) |
| E4 | `pse-lenta` | `AdminPSE.configurar(LENTA)` (acuse tarda más que `AckTimeoutMs`); 200 compras mezcladas | No se agotan los hilos: las compras STRIPE/CRIPTO mantienen su latencia; las PSE responden `OrdenPendientePago` ("verificación en curso") en ≈ `AckTimeoutMs` (no 15 s); tx PSE terminan `TxExpirada` y su orden `OrdenFallida` tras la expiración; el bulkhead PSE no excede su máximo |
| E5 | `cripto-congestion` | `AdminCripto.configurar(CONGESTION)` (callback 5–15 s); 200 compras CRIPTO | Respuesta de `gestionarCompra` < 250 ms P95 aunque el callback tarde ≥ 5 s (RNF-01); sin hilos ICE bloqueados (el hilo del backend responde a otras compras durante la espera); al final todas terminales; **cero** invocaciones a `PersistirTransaccion` originadas en el nodo de pasarelas (verificar por test arquitectónico + por log del nodo BD: el único origen es `transacciones`) |
| E6 | `callback-duplicado` | `AdminStripe.configurar(CALLBACK_DUPLICADO)`; 50 compras STRIPE | Por cada tx: exactamente 1 `TRANSICION` y ≥ 1 `DUPLICADO_IGNORADO` en auditoría; `confirmacionesAplicadas` = 50 (no 100) |
| E7 | `idempotencia` | 5 hilos envían la misma `claveIdempotencia` simultáneamente (repetir con 20 claves) | Un solo `ordenId` devuelto a todos; 1 sola tx por orden; la pasarela recibió 1 solo cobro por orden (`cobrosRecibidos`) |
| E8 | `callback-huerfano` | El cliente de pruebas invoca `NotificarResultadoPago` con un `transaccionId` inexistente | `TransaccionDesconocida`; `contarPorEstado` sin cambios; ninguna fila nueva |
| E9 | `extension` | Con `BILLETERA` habilitada, 20 compras `BILLETERA` | Todas terminan; `git diff --stat v0-sin-billetera..HEAD` sin cambios en `src/main/slice/`, `backend/`, `db/` |
| E10 | arquitectura | Test JUnit/ArchUnit | (a) `pasarelas` no depende de `backend`, `db`; (b) `backend` no depende de `pasarelas`, `db`; (c) `db` no depende de `backend`, `pasarelas`; (d) `cliente` no depende de ningún `*I` servant; (e) ninguna clase referencia `java.net.http`, `HttpURLConnection`, `java.net.URL`, ni dominios `stripe.`/`pse.`; (f) cada interfaz de `02` §3 tiene exactamente un servant proveedor; (g) las 3 estrategias implementan `EstrategiaPagos` |
| E11 | `expiracion` | `AdminPSE.configurar(SIN_CALLBACK)`; 20 compras PSE; luego volver a NORMAL y reinyectar manualmente un callback `TxAprobada` tardío para una de ellas | Tx → `TxExpirada`, orden → `OrdenFallida`; el callback tardío no altera el estado y deja `CONFLICTO_TARDIO` en auditoría; `conflictosTardios` ≥ 1 |

## Pruebas unitarias mínimas (además de E1–E11)
`Histograma` (percentiles), `CircuitBreaker` (todas las transiciones, con `Reloj` falso), `CalculadoraMonto`,
`RepositorioJdbc` (CAS concurrente, rollback), validaciones de cada pasarela, `RepositorioOrdenes` (idempotencia).

## 4. Evidencia a guardar (`docs/evidencia/`, salida REAL, sin edición de resultados)
- `entorno.txt`: versiones (`java -version`, `slice2java -v`, gradle).
- `build.txt`: `./gradlew clean build` completo (resumen de tests).
- `E1_smoke.txt` … `E11_expiracion.txt`: salida del cliente de cada escenario.
- `E9_diff.txt`: `git diff --stat v0-sin-billetera..v1-final` y `git diff --name-only`.
- `arranque_4_procesos.txt`: logs de arranque de los 4 procesos (primeras ~20 líneas de cada uno) mostrando puertos.
- `auditoria_muestra.txt`: auditoría completa de 2 transacciones (una aprobada, una duplicada).
Si un escenario no se puede cumplir en la máquina (p. ej. P95), guarda igualmente la salida y explícalo en
`docs/limitaciones.md`. Nunca ajustes umbrales en silencio para hacerlo pasar.

## 5. Definition of Done por tarea
Compila sin warnings relevantes; tests de la tarea en verde; commit hecho; `PROGRESO.md` y (si hubo errores)
`BITACORA_AGENTE.md` actualizados; sin código muerto ni `TODO` sin issue anotado en `limitaciones.md`.
