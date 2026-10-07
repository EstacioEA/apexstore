# Fragmentos para el informe

Los fragmentos deben copiarse textualmente del código final. Rutas principales:

1. `src/main/slice/apexstore.ice`: interfaces `EstrategiaPagos` y `NotificarResultadoPago`; demuestra el contrato neutro.
2. `src/main/java/co/edu/icesi/apexstore/backend/orquestador/OrquestadorPagosI.java`: `iniciar`; demuestra escritura previa y despacho.
3. `src/main/java/co/edu/icesi/apexstore/backend/orquestador/CircuitBreaker.java`: `permite`, `exito`, `fallo`.
4. `src/main/java/co/edu/icesi/apexstore/pasarelas/PasarelaSimuladaBase.java`: `iniciarCobro`; demuestra Template Method.
5. `src/main/java/co/edu/icesi/apexstore/pasarelas/NotificadorCallback.java`: entrega asíncrona y reintentos.
6. `src/main/java/co/edu/icesi/apexstore/backend/receptor/ReceptorResultadosPagosI.java`: registro idempotente.
7. `src/main/java/co/edu/icesi/apexstore/db/RepositorioJdbc.java`: `transicionar`; CAS y auditoría.
8. `src/main/java/co/edu/icesi/apexstore/backend/checkout/ServicioCheckoutI.java`: idempotencia de órdenes.
9. `src/main/java/co/edu/icesi/apexstore/pasarelas/BilleteraSimulada.java` y `config/*.properties`: extensión RAS-04.
10. `src/main/java/co/edu/icesi/apexstore/arranque/NodoBackend.java`: bootstrap y cableado por proxies.
