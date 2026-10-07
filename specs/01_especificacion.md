# 01 — Especificación funcional y no funcional (QUÉ y POR QUÉ)

## 1. Contexto

ApexStore Technologies es un comercio electrónico con picos x100 (≥ 8 000 peticiones/s en *checkout*). El equipo
analizó un diagrama de despliegue/componentes con tres fallas arquitectónicas (concentración de responsabilidades
en el procesador de pagos, bypass de persistencia por parte de la estrategia Cripto, e interfaces heterogéneas por
pasarela que violan DIP/LSP) y produjo un **rediseño** (Punto 3) que las corrige. Este proyecto es el
**Punto 4: mapear ese rediseño a código Java con ICE**.

## 2. Objetivo

Construir un sistema distribuido en Java/ICE de **4 nodos lógicos** (4 procesos) que implemente el flujo de
compra asíncrono del rediseño, con pasarelas de pago simuladas, persistencia transaccional simulada y un cliente
de prueba que demuestre carga, fallos y extensibilidad.

## 3. Alcance

**Dentro:** contrato Slice; servants de todos los componentes del diagrama; 3 pasarelas simuladas (Stripe, PSE,
Cripto); persistencia con ACID y auditoría (H2 en modo PostgreSQL); resiliencia (timeouts, circuit breaker,
bulkhead, expiración); cliente de carga/escenarios; pruebas de integración automatizadas; documentación y
evidencia para el informe.

**Fuera:** pasarelas reales; HTTPS real (ver deviación D1 en `02`); UI web/móvil real; autenticación de usuarios;
inventario; alta disponibilidad real multi-host; PostgreSQL real; despliegue en contenedores.

## 4. Actores

- **Cliente front-end simulado** (WebApp / MobileApp): proceso CLI que emite solicitudes de compra.
- **Operador de pruebas:** usa interfaces administrativas (módulo Slice `Pruebas`) para inyectar fallos.

## 5. Requisitos funcionales

| ID | Requisito | Origen |
|---|---|---|
| RF-01 | El cliente invoca `GestionarCompraHttp.gestionarCompra`; el sistema responde de inmediato con la orden en estado `OrdenPendientePago` sin esperar la confirmación bancaria. | RAS-01 |
| RF-02 | **Idempotencia de compra:** repetir una solicitud con la misma `claveIdempotencia` devuelve la misma orden y NO crea una segunda transacción ni segundo cobro. | RAS-03 |
| RF-03 | `OrquestadorPagos` (provee `IniciarPagoOrden`) resuelve la estrategia por `codigoMedioPago` mediante un **registro configurable**; un código desconocido produce `MedioPagoNoSoportado`. | RAS-04 |
| RF-04 | Las tres pasarelas (`EstrategiaStripe`, `EstrategiaPSE`, `EstrategiaCripto`) implementan **la misma interfaz** `EstrategiaPagos`. Cada una valida internamente sus propios atributos (ver §6) y rechaza solicitudes inválidas. | RAS-04, DIP/LSP |
| RF-05 | Despacho **síncrono inicial** (acuse rápido `AcuseCobro`) y **confirmación asíncrona**: cada pasarela, tras una latencia simulada, notifica a `ReceptorResultadosPagos.notificarResultadoPago`. **Cripto usa exactamente el mismo canal** (sin acceso a BD). | RAS-01 |
| RF-06 | `ReceptorResultadosPagos` valida el resultado, lo registra vía `RegistrarTransaccion` y notifica a `ServicioCheckout` mediante `ConfirmarCompra`, que actualiza la orden. | RAS-03 |
| RF-07 | El componente `transacciones` implementa `RegistrarTransaccion` y persiste a través de `PersistirTransaccion` (nodo BD). | Diagrama |
| RF-08 | La BD aplica transiciones de estado **atómicas con compare-and-set** (`PENDIENTE → terminal`) y registra **auditoría** en la misma transacción SQL. Unicidad por `orden_id`. | RAS-03 |
| RF-09 | El cliente puede consultar el estado de su orden (`consultarEstado`). | RF-01 |
| RF-10 | **Tolerancia a fallos por estrategia:** timeout de acuse, circuit breaker y bulkhead por medio de pago; el fallo de PSE o Cripto no degrada a Stripe ni a los demás. | RAS-01 |
| RF-11 | **Expiración:** transacciones `TxPendiente` sin resultado tras un umbral configurable pasan a `TxExpirada` y su orden a `OrdenFallida`. | RAS-03 |
| RF-12 | **Extensibilidad:** agregar `BILLETERA` se logra con una clase nueva + entradas de configuración, **sin modificar** Checkout, Orquestador, Receptor, Transacciones, BD ni el Slice. | RAS-04 |
| RF-13 | Cliente de pruebas con escenarios (`smoke`, `carga`, `falla-pse`, `pse-lenta`, `cripto-congestion`, `callback-duplicado`, `idempotencia`, `callback-huerfano`, `expiracion`, `extension`) que imprimen un reporte legible. | Rúbrica P4 |
| RF-14 | Observabilidad: logs con correlación y métricas de latencia de orquestación (P50/P95/P99) consultables por `Pruebas::AdminBackend`. | RAS-02 |

## 6. Datos específicos por pasarela (opacos para el resto del sistema)

Viajan en `SolicitudCompra.datosPago` / `SolicitudCobro.datosPago` y **solo** la pasarela correspondiente los interpreta.

| Código | Pasarela | Atributos obligatorios | Monedas aceptadas | Particularidad simulada |
|---|---|---|---|---|
| `STRIPE` | EstrategiaStripe (tarjeta) | `tokenTarjeta`, `cvcSeguridad` | `USD`, `EUR` | Acuse rápido; callback 200–1500 ms; rechazo configurable |
| `PSE` | EstrategiaPSE (débito bancario) | `codigoBanco`, `tipoDoc`, `numCuenta` | `COP` | Callback más lento (hasta varios segundos); modo "retardo bancario de 15 s" |
| `CRIPTO` | EstrategiaCripto (wallet BTC) | `direccionWallet`, `redBlockchain` | `BTC`, `USD` | Confirmaciones lentas; modo congestión |
| `BILLETERA` (extensión, T-21) | EstrategiaBilletera | `idBilletera`, `pin` | `COP`, `USD` | Demuestra RAS-04 |

Los montos viajan siempre como `long montoMinor` (unidad mínima de la moneda) para evitar errores de coma flotante.
El `montoMinor` de una compra es la suma de `cantidad * precioUnitarioMinor` de sus líneas.

## 7. Requisitos no funcionales y su criterio medible

| ID | Requisito | RAS | Criterio de aceptación |
|---|---|---|---|
| RNF-01 | Asincronía desacoplada | RAS-01 | El tiempo de respuesta de `gestionarCompra` NO depende de la latencia de callback (E5: con callback de 5–15 s la respuesta sigue < 250 ms P95). |
| RNF-02 | Latencia de orquestación | RAS-02 | `iniciarPagoOrden` (medida en servidor, de entrada a salida) **P95 < 250 ms** bajo el escenario E2. Reportar el valor medido real; si no se cumple en la máquina, documentarlo, no maquillarlo. |
| RNF-03 | Integridad ACID | RAS-03 | E2/E6/E7/E8: cero transacciones duplicadas por orden, cero filas huérfanas, cada transición con evento de auditoría. |
| RNF-04 | Extensibilidad | RAS-04 | E9: nueva pasarela sin diff en módulos existentes ni en el Slice. |
| RNF-05 | Simulación pura | Nota del enunciado | E10: ninguna dependencia de red externa ni SDK financiero. |
| RNF-06 | Reproducibilidad | Rúbrica | Compilación y ejecución documentadas, desde repo limpio. |
| RNF-07 | Coherencia diagrama↔código | Rúbrica P4 | Matriz de trazabilidad (`07`) completa y verificada por test arquitectónico. |

Sobre la carga: el objetivo del negocio (8 000 req/s) **no** se puede demostrar en una sola máquina de
desarrollo con 4 procesos. El escenario E2 usa una carga escalada y configurable (por defecto 2 000 compras, 200
hilos concurrentes) y el reporte debe decir explícitamente que es una carga escalada.

## 8. Mapeo a la rúbrica (Punto 4, 1.0 pt)

| Nivel "Excelente" de la rúbrica | Cómo lo cubre este proyecto |
|---|---|
| Implementación completa, modular y funcional con ICE | Slice + 4 nodos + todos los componentes (T-02…T-16) |
| Simula adecuadamente pasarelas de pago | Pasarelas con latencia, fallos y modos inyectables (T-07…T-10) |
| Respeta fielmente distribución, contratos e interfaces | Tabla de cableado `02` §4 + test arquitectónico E10 + matriz de trazabilidad |
| Manejo adecuado de comunicaciones y fallos | Timeouts, breaker, bulkhead, reintentos de callback, expiración (E3–E6, E11) |
