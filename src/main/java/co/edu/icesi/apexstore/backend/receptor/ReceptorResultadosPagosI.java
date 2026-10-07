package co.edu.icesi.apexstore.backend.receptor;

import ApexStore.Checkout.ConfirmarCompraPrx;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.PagoException;
import ApexStore.Comun.ResultadoPago;
import ApexStore.Comun.TransaccionDesconocida;
import ApexStore.Persistencia.RegistrarTransaccionPrx;
import com.zeroc.Ice.Current;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Receptor idempotente de resultados y puente hacia checkout. */
public final class ReceptorResultadosPagosI implements ApexStore.Pagos.NotificarResultadoPago {
    private static final Logger LOG = LoggerFactory.getLogger(ReceptorResultadosPagosI.class);
    private final RegistrarTransaccionPrx transacciones;
    private final ConfirmarCompraPrx checkout;

    public ReceptorResultadosPagosI(RegistrarTransaccionPrx transacciones,
                                    ConfirmarCompraPrx checkout) {
        this.transacciones = transacciones;
        this.checkout = checkout;
    }

    @Override
    public void notificar(ResultadoPago resultado, Current current)
            throws TransaccionDesconocida {
        validar(resultado);
        try {
            boolean cambio = transacciones.registrarResultado(resultado);
            if (!cambio) {
                return;
            }
            checkout.confirmarAsync(resultado).whenComplete((ok, error) -> {
                if (error != null) {
                    LOG.warn("Confirmación no aplicada tx={}", resultado.transaccionId, error);
                }
            });
        } catch (TransaccionDesconocida e) {
            throw e;
        } catch (PagoException e) {
            throw new IllegalStateException("No se pudo registrar callback", e);
        }
    }

    private static void validar(ResultadoPago resultado) {
        if (resultado == null || resultado.transaccionId == null || resultado.transaccionId.isBlank()
                || resultado.ordenId == null || resultado.ordenId.isBlank()
                || resultado.estado == null
                || (resultado.estado != EstadoTransaccion.TxAprobada
                && resultado.estado != EstadoTransaccion.TxRechazada
                && resultado.estado != EstadoTransaccion.TxFallida
                && resultado.estado != EstadoTransaccion.TxExpirada)) {
            throw new IllegalArgumentException("Resultado de pago inválido");
        }
    }
}
