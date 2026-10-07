package co.edu.icesi.apexstore.backend.checkout;

import ApexStore.Comun.AcuseInicioPago;
import ApexStore.Comun.EstadoOrden;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.MedioPagoNoSoportado;
import ApexStore.Comun.OrdenDesconocida;
import ApexStore.Comun.PagoException;
import ApexStore.Comun.ResultadoCompra;
import ApexStore.Comun.ResultadoPago;
import ApexStore.Comun.SolicitudCompra;
import ApexStore.Comun.SolicitudInvalida;
import ApexStore.Comun.ServicioNoDisponible;
import ApexStore.Pagos.IniciarPagoOrdenPrx;
import com.zeroc.Ice.Current;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fachada ICE de checkout, validación e idempotencia de órdenes. */
public final class ServicioCheckoutI {
    private static final Logger LOG = LoggerFactory.getLogger(ServicioCheckoutI.class);
    private final RepositorioOrdenes ordenes;
    private final IniciarPagoOrdenPrx orquestador;
    private final AtomicLong confirmacionesAplicadas = new AtomicLong();

    public ServicioCheckoutI(RepositorioOrdenes ordenes, IniciarPagoOrdenPrx orquestador) {
        this.ordenes = ordenes;
        this.orquestador = orquestador;
    }

    public ResultadoCompra gestionarCompra(SolicitudCompra solicitud, Current current)
            throws SolicitudInvalida, MedioPagoNoSoportado, PagoException {
        validar(solicitud);
        long monto;
        try {
            monto = CalculadoraMonto.calcular(solicitud.lineas);
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw new SolicitudInvalida(e.getMessage());
        }
        RepositorioOrdenes.Orden existente = ordenes.porClave(solicitud.claveIdempotencia);
        if (existente != null) {
            return resultado(existente);
        }
        String nuevoId = co.edu.icesi.apexstore.comun.Ids.nuevoOrden();
        RepositorioOrdenes.Orden orden = ordenes.crearSiNoExiste(solicitud.claveIdempotencia, monto,
                solicitud.moneda, solicitud.codigoMedioPago, nuevoId);
        if (!orden.ordenId.equals(nuevoId)) {
            return resultado(orden);
        }
        try {
            AcuseInicioPago acuse = orquestador.iniciar(new ApexStore.Comun.SolicitudPago(
                    orden.ordenId, monto, solicitud.moneda, solicitud.codigoMedioPago, solicitud.datosPago));
            orden.transaccionId = acuse.transaccionId;
            orden.estado = mapear(acuse.estado);
            orden.mensaje = acuse.mensaje;
        } catch (ServicioNoDisponible e) {
            orden.estado = EstadoOrden.OrdenFallida;
            orden.mensaje = e.razon;
            throw e;
        }
        return resultado(orden);
    }

    public ResultadoCompra consultarEstado(String ordenId, Current current) throws OrdenDesconocida {
        RepositorioOrdenes.Orden orden = ordenes.porId(ordenId);
        if (orden == null) {
            throw new OrdenDesconocida("Orden desconocida", ordenId);
        }
        return resultado(orden);
    }

    public void confirmar(ResultadoPago resultado, Current current) throws OrdenDesconocida {
        RepositorioOrdenes.Orden orden = ordenes.porTransaccion(resultado.transaccionId);
        if (orden == null) {
            throw new OrdenDesconocida("Orden desconocida", resultado.ordenId);
        }
        EstadoOrden nuevo = mapear(resultado.estado);
        synchronized (orden) {
            if (orden.estado == EstadoOrden.OrdenPendientePago) {
                orden.estado = nuevo;
                orden.mensaje = resultado.motivo;
                confirmacionesAplicadas.incrementAndGet();
                LOG.info("[checkout][ord={}|tx={}] estado={}", orden.ordenId,
                        resultado.transaccionId, nuevo);
            }
        }
    }

    public long confirmacionesAplicadas() {
        return confirmacionesAplicadas.get();
    }

    public RepositorioOrdenes repositorio() {
        return ordenes;
    }

    private static void validar(SolicitudCompra s) throws SolicitudInvalida {
        if (s == null || vacio(s.clienteId) || vacio(s.claveIdempotencia)
                || vacio(s.moneda) || vacio(s.codigoMedioPago)
                || s.lineas == null || s.lineas.length == 0) {
            throw new SolicitudInvalida("Campos obligatorios ausentes");
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static EstadoOrden mapear(EstadoTransaccion estado) {
        return switch (estado) {
            case TxPendiente -> EstadoOrden.OrdenPendientePago;
            case TxAprobada -> EstadoOrden.OrdenConfirmada;
            case TxRechazada -> EstadoOrden.OrdenRechazada;
            case TxFallida, TxExpirada -> EstadoOrden.OrdenFallida;
        };
    }

    private static ResultadoCompra resultado(RepositorioOrdenes.Orden orden) {
        return new ResultadoCompra(orden.ordenId, orden.estado, orden.transaccionId, orden.mensaje);
    }
}
