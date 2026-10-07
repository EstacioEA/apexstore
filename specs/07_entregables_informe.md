# 07 — Entregables que el código debe dejar listos para el informe (Punto 4)

El informe final es un PDF que ensambla el equipo humano. Tu trabajo es dejar el material **verídico, trazable y
listo para citar**. Todo fragmento de código que copies al informe debe ser textual del repositorio final.

## 1. `README.md`
Requisitos (JDK, Gradle, `slice2java`), instalación de ICE por SO, compilar, ejecutar en 4 procesos (orden:
DB → Pasarelas → Backend → Cliente) y en modo `runTodo`, cómo correr cada escenario, cómo inyectar fallos,
resolución de problemas (puertos ocupados, versión de ICE desalineada). Debe poder seguirse en < 10 min.

## 2. `docs/trazabilidad.md`
**Tabla A — Diagrama → Código** (una fila por componente, interfaz provista/requerida y conector C1–C8 de `02`):
elemento del diagrama · elemento Slice · clase Java · archivo:línea · prueba que lo verifica.

**Tabla B — Violación → Corrección → Implementación.** Línea base de violaciones del diagrama original (la lista
exacta del informe puede variar; deja la columna "ID en el informe (Punto 1)" vacía para que el equipo la complete):

| Violación del diseño original | Corrección en el rediseño | Dónde se ve en el código | Prueba |
|---|---|---|---|
| Procesador concentraba orquestación + callbacks + persistencia (SRP) | Se separa en `OrquestadorPagos`, `ReceptorResultadosPagos`, `transacciones` | `backend/orquestador`, `backend/receptor`, `backend/transacciones` | E10(b), T-12/T-13 |
| Cripto escribía directo en BD (bypass → pagos huérfanos, ACID roto) | Todas las estrategias notifican por `NotificarResultadoPago`; solo `transacciones` persiste | `pasarelas/*` sin imports de `db`; `NotificadorCallback` | E5, E10(a) |
| Interfaces heterogéneas por pasarela (DIP/LSP) | Interfaz única `EstrategiaPagos` con tipos neutros + `Atributos` opaco | `apexstore.ice`, `PasarelaSimuladaBase` | E10(g), E9 |
| Despacho acoplado a firmas concretas (OCP/RAS-04) | Registro configurable por código de medio | `RegistroEstrategias`, `*.properties` | E9 |
| Sin aislamiento de fallos entre pasarelas | Breaker + bulkhead por medio | `CircuitBreaker`, `Bulkhead` | E3, E4 |

**Tabla C — RAS → Mecanismo → Evidencia** (RAS-01…RAS-04 con el archivo de evidencia y el valor medido real).

## 3. `docs/fragmentos_informe.md`
Entre 8 y 10 fragmentos, **cada uno ≤ 40 líneas**, con comentarios en español, ruta de archivo y una frase de qué
demuestra. Obligatorios: (1) `apexstore.ice` (interfaces `EstrategiaPagos`, `NotificarResultadoPago`); (2)
`OrquestadorPagosI.iniciarPagoOrden`; (3) `CircuitBreaker` (núcleo); (4) `PasarelaSimuladaBase.iniciarCobro`;
(5) `NotificadorCallback` (reintentos); (6) `ReceptorResultadosPagosI.notificarResultadoPago`;
(7) `RepositorioJdbc.transicionar` (CAS+auditoría); (8) `ServicioCheckoutI.gestionarCompra` (idempotencia);
(9) `BilleteraSimulada` + las líneas de config que la habilitan; (10) bootstrap de un nodo (`NodoBackend`).

## 4. `docs/limitaciones.md`
Honesto y concreto: carga escalada vs 8 000 req/s; H2 vs PostgreSQL; TCP vs HTTPS; órdenes en memoria (`02` §10);
conflicto tardío sin reversión automática; cualquier umbral no cumplido y su causa; decisiones de entorno.

## 5. `docs/DECISIONES.md`, `docs/PROGRESO.md`, `docs/entorno.md`
Formato de decisión: `D-XX | contexto | decisión | alternativa descartada | impacto`.

## 6. `docs/BITACORA_AGENTE.md` (insumo de auditoría para el equipo)
Registro cronológico y **veraz** de lo que te ocurrió: cada error de compilación, fallo de prueba, suposición
equivocada, ajuste del contrato o cambio de decisión, con **causa raíz** y **corrección**. Si no hubo errores
en una tarea, no inventes ninguno. Este archivo es la base para que los humanos redacten su evaluación crítica
del uso de IA; su valor está en ser fiel a lo que realmente pasó.

## 7. Capturas
No generes imágenes. Deja salidas de consola completas en `docs/evidencia/`; el equipo tomará las capturas.
