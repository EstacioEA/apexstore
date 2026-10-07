# 03 — Contrato Slice (IDL) normativo

Guarda el bloque siguiente como `src/main/slice/apexstore.ice`. Es el contrato de las interfaces del diagrama
(tabla de `02` §3) más los tipos neutros. Si el compilador reporta un error de sintaxis o de resolución de
nombres, corrígelo preservando nombres y semántica y regístralo en `docs/DECISIONES.md`.

Reglas de diseño del contrato (no negociables):

- Tipos **neutros**: ningún tipo ni operación menciona Stripe, PSE, Cripto, tarjetas, bancos ni wallets.
- Medio de pago = `string codigoMedioPago` (no `enum`), para que agregar medios NO cambie el contrato (RAS-04).
- Datos específicos de cada medio = `Atributos` opaco.
- Dinero = `long` en unidad mínima. Tiempo = `long` epoch millis.
- Los enumeradores de Slice comparten el ámbito del módulo: por eso llevan prefijo (`Tx…`, `Orden…`); dos enums del
  mismo módulo no pueden repetir un enumerador.

```slice
#pragma once

module ApexStore
{
    // =====================================================================
    // Tipos y excepciones neutros compartidos
    // =====================================================================
    module Comun
    {
        dictionary<string, string> Atributos;

        enum EstadoTransaccion { TxPendiente, TxAprobada, TxRechazada, TxFallida, TxExpirada };
        enum EstadoOrden { OrdenPendientePago, OrdenConfirmada, OrdenRechazada, OrdenFallida };

        struct LineaCompra
        {
            string sku;
            int cantidad;
            long precioUnitarioMinor;
        };
        sequence<LineaCompra> LineasCompra;

        // --- Cliente <-> Checkout ---
        struct SolicitudCompra
        {
            string clienteId;
            string claveIdempotencia;
            string moneda;
            string codigoMedioPago;      // "STRIPE" | "PSE" | "CRIPTO" | "BILLETERA" | ...
            LineasCompra lineas;
            Atributos datosPago;         // opaco para todo el sistema salvo la pasarela
        };

        struct ResultadoCompra
        {
            string ordenId;
            EstadoOrden estado;
            string transaccionId;        // vacío si aún no existe
            string mensaje;
        };

        // --- Checkout <-> Orquestador ---
        struct SolicitudPago
        {
            string ordenId;
            long montoMinor;
            string moneda;
            string codigoMedioPago;
            Atributos datosPago;
        };

        struct AcuseInicioPago
        {
            string transaccionId;
            EstadoTransaccion estado;    // TxPendiente (normal) | TxRechazada (rechazo síncrono)
            string mensaje;
        };

        // --- Orquestador <-> Estrategia (interfaz uniforme) ---
        struct SolicitudCobro
        {
            string transaccionId;
            string ordenId;
            long montoMinor;
            string moneda;
            Atributos datosPago;
        };

        struct AcuseCobro
        {
            bool aceptado;
            string referenciaExterna;
            string motivo;
        };

        // --- Estrategia -> Receptor (callback asíncrono) ---
        struct ResultadoPago
        {
            string transaccionId;
            string ordenId;
            EstadoTransaccion estado;    // TxAprobada | TxRechazada | TxFallida
            string referenciaExterna;
            string motivo;
            string codigoMedioPago;
            long marcaTiempoMs;
        };

        // --- Persistencia ---
        struct Transaccion
        {
            string transaccionId;
            string ordenId;
            string codigoMedioPago;
            long montoMinor;
            string moneda;
            EstadoTransaccion estado;
            string referenciaExterna;
            string motivo;
            long creadaMs;
            long actualizadaMs;
        };
        sequence<Transaccion> Transacciones;

        struct EventoAuditoria
        {
            long id;
            string transaccionId;
            string evento;               // CREADA, TRANSICION, DUPLICADO_IGNORADO, CONFLICTO_TARDIO, EXPIRADA
            string estadoAnterior;
            string estadoNuevo;
            string detalle;
            long marcaTiempoMs;
        };
        sequence<EventoAuditoria> EventosAuditoria;

        dictionary<string, long> ConteoPorEstado;   // clave = nombre del estado

        // --- Excepciones ---
        exception PagoException { string razon; };
        exception SolicitudInvalida extends PagoException { };
        exception MedioPagoNoSoportado extends PagoException { string codigoMedioPago; };
        exception ServicioNoDisponible extends PagoException { string componente; };
        exception TransaccionDesconocida extends PagoException { string transaccionId; };
        exception TransaccionDuplicada extends PagoException { string ordenId; };
        exception OrdenDesconocida extends PagoException { string ordenId; };
    };

    // =====================================================================
    // Interfaces de Checkout (nodo Backend)
    // =====================================================================
    module Checkout
    {
        // Provista por ServicioCheckout; requerida por WebApp / MobileApp
        interface GestionarCompraHttp
        {
            Comun::ResultadoCompra gestionarCompra(Comun::SolicitudCompra solicitud)
                throws Comun::SolicitudInvalida, Comun::MedioPagoNoSoportado, Comun::PagoException;

            idempotent Comun::ResultadoCompra consultarEstado(string ordenId)
                throws Comun::OrdenDesconocida;
        };

        // Provista por ServicioCheckout; requerida por ReceptorResultadosPagos
        interface ConfirmarCompra
        {
            idempotent void confirmarCompra(Comun::ResultadoPago resultado)
                throws Comun::OrdenDesconocida;
        };
    };

    // =====================================================================
    // Interfaces de Pagos (Backend y nodo de Pasarelas)
    // =====================================================================
    module Pagos
    {
        // Provista por OrquestadorPagos; requerida por ServicioCheckout
        interface IniciarPagoOrden
        {
            Comun::AcuseInicioPago iniciarPagoOrden(Comun::SolicitudPago solicitud)
                throws Comun::MedioPagoNoSoportado, Comun::ServicioNoDisponible, Comun::SolicitudInvalida;
        };

        // Provista por TODAS las estrategias (interfaz abstracta única: DIP/LSP)
        interface EstrategiaPagos
        {
            idempotent string codigoMedioPago();

            Comun::AcuseCobro iniciarCobro(Comun::SolicitudCobro solicitud)
                throws Comun::SolicitudInvalida, Comun::ServicioNoDisponible;
        };

        // Provista por ReceptorResultadosPagos; requerida por las estrategias
        interface NotificarResultadoPago
        {
            idempotent void notificarResultadoPago(Comun::ResultadoPago resultado)
                throws Comun::TransaccionDesconocida;
        };
    };

    // =====================================================================
    // Interfaces de Persistencia
    // =====================================================================
    module Persistencia
    {
        // Provista por el componente `transacciones`; requerida por Orquestador y Receptor
        interface RegistrarTransaccion
        {
            void registrarPendiente(Comun::Transaccion tx)
                throws Comun::TransaccionDuplicada, Comun::PagoException;

            // true = cambió el estado; false = duplicado/conflicto ignorado
            bool registrarResultado(Comun::ResultadoPago resultado)
                throws Comun::TransaccionDesconocida, Comun::PagoException;

            idempotent Comun::Transaccion obtener(string transaccionId)
                throws Comun::TransaccionDesconocida;

            // Expira TxPendiente creadas antes de `antesDeMs`; devuelve las transacciones expiradas
            Comun::Transacciones expirarPendientes(long antesDeMs);
        };

        // Provista por DB_PostgreSQL_Transacciones; requerida por `transacciones`
        interface PersistirTransaccion
        {
            void insertar(Comun::Transaccion tx)
                throws Comun::TransaccionDuplicada, Comun::PagoException;

            // Compare-and-set atómico + auditoría en la misma transacción SQL.
            // true = transición aplicada; false = el estado actual ya no era `esperado`.
            bool transicionar(string transaccionId,
                              Comun::EstadoTransaccion esperado,
                              Comun::EstadoTransaccion nuevo,
                              string referenciaExterna,
                              string motivo)
                throws Comun::TransaccionDesconocida;

            // Registra un evento de auditoría sin cambiar estado (duplicados, conflictos)
            void auditar(string transaccionId, string evento, string detalle);

            idempotent Comun::Transaccion obtener(string transaccionId)
                throws Comun::TransaccionDesconocida;
            idempotent Comun::Transaccion obtenerPorOrden(string ordenId)
                throws Comun::TransaccionDesconocida;
            idempotent Comun::EventosAuditoria auditoria(string transaccionId);
            idempotent Comun::ConteoPorEstado contarPorEstado();

            Comun::Transacciones expirarPendientes(long antesDeMs);
        };
    };

    // =====================================================================
    // Arnés de pruebas (NO forma parte del diagrama; desviación D3)
    // =====================================================================
    module Pruebas
    {
        // modo: NORMAL | CAIDA | LENTA | CONGESTION | SIN_CALLBACK | CALLBACK_DUPLICADO
        struct ConfigPasarela
        {
            string modo;
            int latenciaAckMinMs;
            int latenciaAckMaxMs;
            int latenciaCallbackMinMs;
            int latenciaCallbackMaxMs;
            double tasaRechazo;          // 0.0 .. 1.0
        };

        interface AdminPasarela
        {
            void configurar(ConfigPasarela cfg);
            idempotent ConfigPasarela configuracion();
            idempotent long cobrosRecibidos();
            idempotent long callbacksEnviados();
            void reiniciarContadores();
        };

        struct MetricasLatencia
        {
            long muestras;
            double p50Ms;
            double p95Ms;
            double p99Ms;
            double maxMs;
        };

        dictionary<string, string> EstadoCircuitos;   // codigoMedioPago -> CERRADO|ABIERTO|SEMIABIERTO

        interface AdminBackend
        {
            idempotent MetricasLatencia latenciaOrquestacion();
            idempotent EstadoCircuitos estadoCircuitos();
            idempotent long conflictosTardios();
            idempotent long confirmacionesAplicadas();   // veces que ConfirmarCompra cambió una orden
            void reiniciarMetricas();
        };
    };
};
```

## Semántica de operaciones (complemento normativo)

| Operación | Precondiciones / validaciones | Postcondiciones |
|---|---|---|
| `gestionarCompra` | `clienteId`, `claveIdempotencia`, `moneda`, `codigoMedioPago` no vacíos; ≥ 1 línea; `cantidad > 0`; `precioUnitarioMinor > 0` → si no, `SolicitudInvalida` | Orden creada (o la existente si la clave se repite). Respuesta inmediata. |
| `consultarEstado` | `ordenId` existente | Estado actual de la orden |
| `iniciarPagoOrden` | medio registrado; monto > 0 | Existe una `Transaccion` en BD en `TxPendiente` (o ya `TxFallida`/`TxRechazada` en fallos definitivos / rechazo síncrono) |
| `iniciarCobro` | atributos y moneda válidos para esa pasarela | Devuelve acuse en ms; programa el callback asíncrono |
| `notificarResultadoPago` | `transaccionId` existe | Transición CAS aplicada una sola vez; `ConfirmarCompra` invocado solo si hubo cambio |
| `registrarResultado` | tx existente | `true` si cambió; `false` si ya era terminal (se audita duplicado/conflicto) |
| `transicionar` | — | Atómica: UPDATE condicionado por estado + INSERT en auditoría en una sola transacción SQL |
| `expirarPendientes` | — | Cada tx expirada pasó `TxPendiente→TxExpirada` con auditoría |
