package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Comun.ResultadoPago;
import ApexStore.Pagos.NotificarResultadoPagoPrx;
import com.zeroc.Ice.LocalException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Entrega callbacks at-least-once sin bloquear hilos de ICE. */
public final class NotificadorCallback {
    private final NotificarResultadoPagoPrx receptor;
    private final ScheduledExecutorService scheduler;
    private final int maxIntentos;
    private final long backoffInicialMs;
    private final AtomicLong callbacksEnviados = new AtomicLong();

    public NotificadorCallback(NotificarResultadoPagoPrx receptor,
                               ScheduledExecutorService scheduler,
                               int maxIntentos, long backoffInicialMs) {
        this.receptor = receptor;
        this.scheduler = scheduler;
        this.maxIntentos = maxIntentos;
        this.backoffInicialMs = backoffInicialMs;
    }

    public void enviar(ResultadoPago resultado) {
        intentar(resultado, 1);
    }

    public long enviados() {
        return callbacksEnviados.get();
    }

    private void intentar(ResultadoPago resultado, int intento) {
        receptor.notificarAsync(resultado).whenComplete((ok, error) -> {
            if (error == null) {
                callbacksEnviados.incrementAndGet();
                return;
            }
            if (intento < maxIntentos && esReintentable(error)) {
                long demora = backoffInicialMs * (1L << Math.min(intento - 1, 10));
                scheduler.schedule(() -> intentar(resultado, intento + 1), demora, TimeUnit.MILLISECONDS);
            }
        });
    }

    private static boolean esReintentable(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null) {
            actual = actual.getCause();
        }
        return actual instanceof LocalException;
    }
}
