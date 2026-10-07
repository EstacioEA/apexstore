# 05 — Tareas (ejecutar en orden; una compuerta no se cruza sin cumplirla)

Convenciones: al terminar cada tarea → ejecutar su verificación → commit `T-XX: …` → actualizar
`docs/PROGRESO.md` (estado, commit, fecha) → registrar en `docs/BITACORA_AGENTE.md` cualquier error real hallado.
Referencias: `RF/RNF` en `01`, `§` en `02`/`04`, `E#` en `06`.

## Fase 0 — Entorno y esqueleto

**T-00 Diagnóstico de entorno.** Detectar SO, JDK, Gradle, `slice2java -v`. Escribir `docs/entorno.md` con las
versiones reales. Si falta `slice2java` y no se puede instalar → `docs/BLOQUEO.md` y detenerse.
*Aceptación:* `docs/entorno.md` existe; versión de ICE elegida = `slice2java -v` (mayor.menor).

**T-01 Repositorio y esqueleto de build.** `git init`, `.gitignore` (`build/`, `.gradle/`, `data/`, IDE), estructura
de `04` §2, `build.gradle`, `settings.gradle`, `gradle.properties` (`iceVersion`, `h2Version`), copia de `AGENTS.md`
y `specs/`. Slice mínimo de prueba (`module Humo { interface Eco { string eco(string s); }; }`) para validar la
cadena de compilación; se elimina en T-02.
*Aceptación:* `./gradlew build` compila el Slice de humo y genera clases `com.zeroc.Ice`-based. **Gate G0.**

## Fase 1 — Contratos

**T-02 Contrato Slice.** Crear `src/main/slice/apexstore.ice` desde `03`. Quitar el Slice de humo.
*Aceptación:* `./gradlew compileJava` OK; existen `ApexStore.Checkout.GestionarCompraHttpPrx`,
`ApexStore.Pagos.EstrategiaPagosPrx`, etc. Anotar cualquier ajuste del contrato en `DECISIONES.md`.

**T-03 Utilidades comunes.** `comun/*`: `Ids`, `Reloj` (interfaz + sistema), `Histograma` (+ test unitario de
percentiles), `ConfigUtil` (parser de rangos `"min-max"`), `Correlacion`. **Gate G1** (contratos + utilidades compilando, tests unitarios en verde).

## Fase 2 — Nodo 4: Base de datos

**T-04 Esquema y repositorio JDBC.** `EsquemaSql`, `RepositorioJdbc` con `insertar`, `transicionar` (CAS +
auditoría), `auditar`, `obtener`, `obtenerPorOrden`, `auditoria`, `contarPorEstado`, `expirarPendientes`.
*Aceptación:* tests unitarios con H2 en memoria: inserción duplicada por `orden_id` → excepción; transición
concurrente (32 hilos sobre la misma tx) → **exactamente una** aplica; auditoría con N eventos esperados;
`rollback` demostrado cuando falla a mitad (inyectar fallo).

**T-05 Servant y servidor de BD.** `PersistirTransaccionI` (traduce SQL→excepciones Slice), `NodoDb`, `DbServer`
(main). *Aceptación:* `runDb` arranca, queda escuchando en 10200; un test llama por proxy `insertar/obtener`.

## Fase 3 — Nodo 3: Pasarelas

**T-06 Base de simulación.** `ConfigSimulacion` (modos de `03`), `PasarelaSimuladaBase` (Template Method),
`NotificadorCallback` (reintentos con backoff), `AdminPasarelaI`.
**T-07 Pasarelas concretas.** `StripeSimulada`, `PseSimulada`, `CriptoSimulada` con su `validar` (`01` §6).
*Aceptación T-06/T-07:* tests unitarios con un receptor falso (mock de `NotificarResultadoPago`): callback llega
una vez en modo NORMAL; dos veces en CALLBACK_DUPLICADO; ninguna en SIN_CALLBACK; `SolicitudInvalida` por
atributos/moneda erróneos; `ServicioNoDisponible` en CAIDA; mismo `transaccionId` dos veces → un solo cobro.
**T-08 Servidor de pasarelas.** `NodoPasarelas`, `PasarelasServer` (instanciación **por reflexión** desde
`Pasarela.<COD>.Clase`, `02`/`04` §4). **Gate G2** (nodos 3 y 4 arrancan y responden por proxy).

## Fase 4 — Nodo 2: Backend

**T-09 `transacciones`.** `TransaccionesI` (`04` §5.3). *Aceptación:* test con BD real (T-05): flujo
pendiente→resultado; duplicado → `false` + auditoría `DUPLICADO_IGNORADO`; conflicto → `CONFLICTO_TARDIO`; id
inexistente → `TransaccionDesconocida`.
**T-10 Checkout.** `RepositorioOrdenes`, `CalculadoraMonto`, `ServicioCheckoutI` (`04` §5.1). *Aceptación:* tests con orquestador
falso: validaciones, idempotencia concurrente (20 hilos misma clave → 1 orden), mapeos de estado.
**T-11 Resiliencia.** `CircuitBreaker` (con `Reloj` inyectable), `Bulkhead`; tests de transiciones de estado.
**T-12 Orquestador.** `RegistroEstrategias`, `OrquestadorPagosI` (`04` §5.2) + `MetricasBackend`.
**T-13 Receptor.** `ReceptorResultadosPagosI` (`04` §5.4). **T-14 Sweeper.** `SweeperExpiracion` (`04` §5.5).
**T-15 Admin y servidor.** `AdminBackendI`, `NodoBackend`, `BackendServer` (main).
*Aceptación fase 4:* backend arranca con los otros dos nodos; una compra manual (vía test) llega a `OrdenConfirmada`
con tx `TxAprobada` y ≥ 2 eventos de auditoría. **Gate G3.**

## Fase 5 — Integración y cliente

**T-16 Arranque unificado.** `RunAll` (4 `Communicator`, puertos configurables, parada limpia) y tareas Gradle
`runDb|runPasarelas|runBackend|runTodo|runCliente`; scripts de `scripts/`.
**T-17 Cliente y escenarios.** `ClienteApp`, `CanalWeb`, `CanalMovil`, `ReporteConsola` y un escenario por cada
`RF-13`/`06`. Reporte por escenario: parámetros, conteos por estado, P50/P95/P99 del cliente y de orquestación, PASS/FAIL
de cada aserción.
**T-18 Pruebas de integración JUnit** E1–E9 y E11 con `RunAll` en puertos libres y tiempos reducidos
(`config/test.properties`). **T-19 Test arquitectónico** E10 (ArchUnit + escaneo de dependencias de red).
*Aceptación fase 5:* `./gradlew clean test` en verde; salidas reales guardadas en `docs/evidencia/` (`06` §4). **Gate G4.**

## Fase 6 — Extensibilidad (RAS-04)

**T-20 Etiqueta previa:** `git tag v0-sin-billetera`.
**T-21 `BilleteraSimulada`.** Una clase en `pasarelas/` + entradas en `pasarelas.properties` y
`backend.properties` (`Orquestador.Medios=STRIPE,PSE,CRIPTO,BILLETERA`, proxy). Escenario `extension`.
*Aceptación:* E9: `git diff --stat v0-sin-billetera..HEAD` muestra cambios **únicamente** en
`pasarelas/BilleteraSimulada.java`, `config/*.properties`, tests/docs; **cero** cambios en `src/main/slice/`,
`backend/`, `db/`, `cliente/` (salvo agregar el escenario). Guardar la salida del diff en `docs/evidencia/E9_diff.txt`.

## Fase 7 — Entregables para el informe

**T-22 README** (compilar, ejecutar 4 procesos y modo `runTodo`, correr escenarios, troubleshooting de `slice2java`).
**T-23 Evidencia** completa (`06` §4). **T-24 `docs/trazabilidad.md`** y **T-25 `docs/fragmentos_informe.md`**
(`07`). **T-26 `docs/limitaciones.md`** y cierre de `docs/BITACORA_AGENTE.md`.
**T-27 Verificación final desde cero:** clonar el repo a un directorio nuevo, `./gradlew clean build`, ejecutar
`runTodo` + escenarios `smoke` y `carga`; `git tag v1-final`. **Gate FINAL = Definition of Done de `AGENTS.md` §5.**

## Priorización si hay poco tiempo (no recortar sin registrarlo)
Obligatorio: T-00…T-19, T-22, T-23, T-27. Importante: T-21 (RAS-04), T-14 (expiración). Diferible: correlación por
*context*, scripts `.ps1` si el SO es Unix (y viceversa).
