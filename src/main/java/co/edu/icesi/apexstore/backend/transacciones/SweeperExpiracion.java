package co.edu.icesi.apexstore.backend.transacciones;

import ApexStore.Checkout.ConfirmarCompraPrx;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.ResultadoPago;
import ApexStore.Persistencia.RegistrarTransaccionPrx;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Expira transacciones pendientes y actualiza la orden de forma asíncrona. */
public final class SweeperExpiracion implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SweeperExpiracion.class);
    private final ScheduledFuture<?> tarea;

    public SweeperExpiracion(RegistrarTransaccionPrx transacciones,
                             ConfirmarCompraPrx checkout,
                             ScheduledExecutorService scheduler,
                             long pendienteMs, long periodoMs) {
        tarea = scheduler.scheduleAtFixedRate(() -> ejecutar(transacciones, checkout, pendienteMs),
                periodoMs, periodoMs, TimeUnit.MILLISECONDS);
    }

    private static void ejecutar(RegistrarTransaccionPrx transacciones,
                                 ConfirmarCompraPrx checkout, long pendienteMs) {
        try {
            for (var tx : transacciones.expirarPendientes(System.currentTimeMillis() - pendienteMs)) {
                checkout.confirmarAsync(new ResultadoPago(tx.transaccionId, tx.ordenId,
                        EstadoTransaccion.TxExpirada, "", "Expirada", tx.codigoMedioPago,
                        System.currentTimeMillis()));
            }
        } catch (RuntimeException error) {
            LOG.warn("Error en sweeper de expiración; se reintentará en el próximo ciclo", error);
        }
    }

    @Override
    public void close() {
        tarea.cancel(false);
    }
}
