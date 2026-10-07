# Trazabilidad

## Diagrama → código

| Elemento | Slice | Código | Evidencia |
|---|---|---|---|
| ServicioCheckout | `GestionarCompraHttp`, `ConfirmarCompra` | `backend/checkout/ServicioCheckoutI.java` y servants separados | `BackendUnitTest` |
| OrquestadorPagos | `IniciarPagoOrden` | `backend/orquestador/OrquestadorPagosI.java` | `BackendUnitTest` |
| Estrategias | `EstrategiaPagos` | `pasarelas/*Simulada.java` | `PasarelasTest` |
| Receptor | `NotificarResultadoPago` | `backend/receptor/ReceptorResultadosPagosI.java` | compilación Slice |
| transacciones | `RegistrarTransaccion` | `backend/transacciones/TransaccionesI.java` | `RepositorioJdbcTest` |
| DB | `PersistirTransaccion` | `db/PersistirTransaccionI.java`, `RepositorioJdbc.java` | `PersistirTransaccionITest` |
| C1–C8 | proxies ICE en configuración | `config/*.properties`, `NodoBackend`, `NodoPasarelas` | `E1_smoke.txt` |

## Violación → corrección

| Violación | Corrección | Implementación |
|---|---|---|
| Orquestación y callbacks mezclados | Separación de orquestador/receptor | `backend/orquestador`, `backend/receptor` |
| Cripto escribía en BD | Callback uniforme al receptor | `CriptoSimulada` |
| Interfaces heterogéneas | `EstrategiaPagos` común y `Atributos` opaco | `apexstore.ice` |
| Sin aislamiento | breaker y bulkhead por medio | `CircuitBreaker`, `Bulkhead` |

## RAS → evidencia

| RAS | Mecanismo | Evidencia |
|---|---|---|
| RAS-01 | acuse síncrono y callback programado | `E1_smoke.txt` |
| RAS-02 | `MetricasBackend` + `Histograma` | tests unitarios |
| RAS-03 | CAS JDBC + auditoría | `T04_test.txt` |
| RAS-04 | reflexión y BILLETERA por configuración | `E9_diff.txt`, `E9_extension.txt` |
