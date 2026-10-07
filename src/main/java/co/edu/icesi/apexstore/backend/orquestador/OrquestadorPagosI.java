package co.edu.icesi.apexstore.backend.orquestador;

import ApexStore.Comun.AcuseCobro;
import ApexStore.Comun.AcuseInicioPago;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.MedioPagoNoSoportado;
import ApexStore.Comun.PagoException;
import ApexStore.Comun.ServicioNoDisponible;
import ApexStore.Comun.SolicitudCobro;
import ApexStore.Comun.SolicitudInvalida;
import ApexStore.Comun.SolicitudPago;
import ApexStore.Comun.Transaccion;
import ApexStore.Pagos.EstrategiaPagosPrx;
import ApexStore.Pagos.IniciarPagoOrden;
import ApexStore.Persistencia.RegistrarTransaccionPrx;
import co.edu.icesi.apexstore.backend.admin.MetricasBackend;
import co.edu.icesi.apexstore.comun.Ids;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.LocalException;
import com.zeroc.Ice.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orquestador que registra pendiente antes de despachar el cobro. */
public final class OrquestadorPagosI implements IniciarPagoOrden {
    private static final Logger LOG = LoggerFactory.getLogger(OrquestadorPagosI.class);
    private final RegistroEstrategias registro;
    private final RegistrarTransaccionPrx transacciones;
    private final MetricasBackend metricas;

    public OrquestadorPagosI(RegistroEstrategias registro,
                             RegistrarTransaccionPrx transacciones,
                             MetricasBackend metricas) {
        this.registro = registro;
        this.transacciones = transacciones;
        this.metricas = metricas;
    }

    @Override
    public AcuseInicioPago iniciar(SolicitudPago solicitud, Current current)
            throws MedioPagoNoSoportado, ServicioNoDisponible, SolicitudInvalida {
        validar(solicitud);
        long inicio = System.nanoTime();
        EntradaEstrategia entrada = registro.resolver(solicitud.codigoMedioPago);
        String txId = Ids.nuevaTransaccion();
        long ahora = System.currentTimeMillis();
        Transaccion tx = new Transaccion(txId, solicitud.ordenId, solicitud.codigoMedioPago,
                solicitud.montoMinor, solicitud.moneda, EstadoTransaccion.TxPendiente,
                "", "", ahora, ahora);
        try {
            transacciones.registrarPendiente(tx);
            if (!entrada.breaker().permite()) {
                fallar(txId, "CIRCUITO_ABIERTO");
                throw new ServicioNoDisponible("Circuito abierto", solicitud.codigoMedioPago);
            }
            if (!entrada.bulkhead().tryAcquire()) {
                fallar(txId, "SATURADA");
                throw new ServicioNoDisponible("Bulkhead saturado", solicitud.codigoMedioPago);
            }
            try {
                EstrategiaPagosPrx estrategia = entrada.proxy();
                AcuseCobro acuse;
                try {
                    acuse = estrategia.iniciarCobro(new SolicitudCobro(txId, solicitud.ordenId,
                            solicitud.montoMinor, solicitud.moneda, solicitud.datosPago));
                    entrada.breaker().exito();
                } catch (TimeoutException timeout) {
                    entrada.breaker().fallo();
                    return new AcuseInicioPago(txId, EstadoTransaccion.TxPendiente,
                            "verificación en curso");
                } catch (LocalException error) {
                    entrada.breaker().fallo();
                    fallar(txId, "PASARELA_NO_DISPONIBLE");
                    throw new ServicioNoDisponible("Pasarela no disponible",
                            solicitud.codigoMedioPago);
                }
                if (!acuse.aceptado) {
                    transacciones.registrarResultado(new ApexStore.Comun.ResultadoPago(
                            txId, solicitud.ordenId, EstadoTransaccion.TxRechazada,
                            acuse.referenciaExterna, acuse.motivo, solicitud.codigoMedioPago,
                            System.currentTimeMillis()));
                    return new AcuseInicioPago(txId, EstadoTransaccion.TxRechazada, acuse.motivo);
                }
                return new AcuseInicioPago(txId, EstadoTransaccion.TxPendiente, "Cobro despachado");
            } finally {
                entrada.bulkhead().release();
            }
        } catch (ServicioNoDisponible e) {
            throw e;
        } catch (PagoException e) {
            throw new ServicioNoDisponible("Persistencia no disponible", "transacciones");
        } finally {
            metricas.registrarLatencia((System.nanoTime() - inicio) / 1_000_000.0);
        }
    }

    private void fallar(String txId, String motivo) {
        try {
            transacciones.registrarResultado(new ApexStore.Comun.ResultadoPago(
                    txId, "", EstadoTransaccion.TxFallida, "", motivo, "", System.currentTimeMillis()));
        } catch (Exception e) {
            LOG.warn("No se pudo registrar fallo tx={}", txId, e);
        }
    }

    private static void validar(SolicitudPago solicitud) throws SolicitudInvalida {
        if (solicitud == null || solicitud.ordenId == null || solicitud.ordenId.isBlank()
                || solicitud.montoMinor <= 0 || solicitud.moneda == null || solicitud.moneda.isBlank()
                || solicitud.codigoMedioPago == null || solicitud.codigoMedioPago.isBlank()) {
            throw new SolicitudInvalida("Solicitud de pago inválida");
        }
    }
}
