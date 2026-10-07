#pragma once

module ApexStore
{
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

        struct SolicitudCompra
        {
            string clienteId;
            string claveIdempotencia;
            string moneda;
            string codigoMedioPago;
            LineasCompra lineas;
            Atributos datosPago;
        };

        struct ResultadoCompra
        {
            string ordenId;
            EstadoOrden estado;
            string transaccionId;
            string mensaje;
        };

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
            EstadoTransaccion estado;
            string mensaje;
        };

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

        struct ResultadoPago
        {
            string transaccionId;
            string ordenId;
            EstadoTransaccion estado;
            string referenciaExterna;
            string motivo;
            string codigoMedioPago;
            long marcaTiempoMs;
        };

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
            string evento;
            string estadoAnterior;
            string estadoNuevo;
            string detalle;
            long marcaTiempoMs;
        };
        sequence<EventoAuditoria> EventosAuditoria;

        dictionary<string, long> ConteoPorEstado;

        exception PagoException { string razon; };
        exception SolicitudInvalida extends PagoException { };
        exception MedioPagoNoSoportado extends PagoException { string codigoMedioPago; };
        exception ServicioNoDisponible extends PagoException { string componente; };
        exception TransaccionDesconocida extends PagoException { string transaccionId; };
        exception TransaccionDuplicada extends PagoException { string ordenId; };
        exception OrdenDesconocida extends PagoException { string ordenId; };
    };

    module Checkout
    {
        interface GestionarCompraHttp
        {
            Comun::ResultadoCompra gestionarCompra(Comun::SolicitudCompra solicitud)
                throws Comun::SolicitudInvalida, Comun::MedioPagoNoSoportado, Comun::PagoException;

            idempotent Comun::ResultadoCompra consultarEstado(string ordenId)
                throws Comun::OrdenDesconocida;
        };

        interface ConfirmarCompra
        {
            idempotent void confirmar(Comun::ResultadoPago resultado)
                throws Comun::OrdenDesconocida;
        };
    };

    module Pagos
    {
        interface IniciarPagoOrden
        {
            Comun::AcuseInicioPago iniciar(Comun::SolicitudPago solicitud)
                throws Comun::MedioPagoNoSoportado, Comun::ServicioNoDisponible, Comun::SolicitudInvalida;
        };

        interface EstrategiaPagos
        {
            idempotent string codigoMedioPago();

            Comun::AcuseCobro iniciarCobro(Comun::SolicitudCobro solicitud)
                throws Comun::SolicitudInvalida, Comun::ServicioNoDisponible;
        };

        interface NotificarResultadoPago
        {
            idempotent void notificar(Comun::ResultadoPago resultado)
                throws Comun::TransaccionDesconocida;
        };
    };

    module Persistencia
    {
        interface RegistrarTransaccion
        {
            void registrarPendiente(Comun::Transaccion tx)
                throws Comun::TransaccionDuplicada, Comun::PagoException;

            bool registrarResultado(Comun::ResultadoPago resultado)
                throws Comun::TransaccionDesconocida, Comun::PagoException;

            idempotent Comun::Transaccion obtener(string transaccionId)
                throws Comun::TransaccionDesconocida;

            Comun::Transacciones expirarPendientes(long antesDeMs);
        };

        interface PersistirTransaccion
        {
            void insertar(Comun::Transaccion tx)
                throws Comun::TransaccionDuplicada, Comun::PagoException;

            bool transicionar(string transaccionId,
                              Comun::EstadoTransaccion esperado,
                              Comun::EstadoTransaccion nuevo,
                              string referenciaExterna,
                              string motivo)
                throws Comun::TransaccionDesconocida;

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

    module Pruebas
    {
        struct ConfigPasarela
        {
            string modo;
            int latenciaAckMinMs;
            int latenciaAckMaxMs;
            int latenciaCallbackMinMs;
            int latenciaCallbackMaxMs;
            double tasaRechazo;
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

        dictionary<string, string> EstadoCircuitos;

        interface AdminBackend
        {
            idempotent MetricasLatencia latenciaOrquestacion();
            idempotent EstadoCircuitos obtenerEstadoCircuitos();
            idempotent long conflictosTardios();
            idempotent long confirmacionesAplicadas();
            void reiniciarMetricas();
        };
    };
};
