package co.edu.icesi.apexstore.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ApexStore.Comun.AcuseInicioPago;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.LineaCompra;
import ApexStore.Comun.SolicitudCompra;
import ApexStore.Pagos.IniciarPagoOrdenPrx;
import co.edu.icesi.apexstore.backend.checkout.ServicioCheckoutI;
import co.edu.icesi.apexstore.backend.checkout.RepositorioOrdenes;
import co.edu.icesi.apexstore.backend.orquestador.CircuitBreaker;
import co.edu.icesi.apexstore.comun.Reloj;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackendUnitTest {
    @Test
    void checkoutEsIdempotenteConcurrente() throws Exception {
        try (Communicator communicator = Util.initialize()) {
            AtomicInteger invocaciones = new AtomicInteger();
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("orq", "tcp -p 0");
            adapter.add((ApexStore.Pagos.IniciarPagoOrden) (solicitud, current) -> {
                invocaciones.incrementAndGet();
                return new AcuseInicioPago("tx-1", EstadoTransaccion.TxPendiente, "ok");
            }, Util.stringToIdentity("Orquestador"));
            adapter.activate();
            IniciarPagoOrdenPrx proxy = IniciarPagoOrdenPrx.uncheckedCast(
                    adapter.createProxy(Util.stringToIdentity("Orquestador")));
            ServicioCheckoutI checkout = new ServicioCheckoutI(new RepositorioOrdenes(), proxy);
            SolicitudCompra compra = new SolicitudCompra("cliente", "clave", "USD", "STRIPE",
                    new LineaCompra[]{new LineaCompra("sku", 1, 100)},
                    Map.of("tokenTarjeta", "tok", "cvcSeguridad", "123"));
            var pool = Executors.newFixedThreadPool(20);
            try {
                List<Callable<String>> tareas = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    tareas.add(() -> checkout.gestionarCompra(compra, new Current()).ordenId);
                }
                List<Future<String>> resultados = pool.invokeAll(tareas);
                assertEquals(1, resultados.stream().map(f -> get(f)).distinct().count());
                assertEquals(1, invocaciones.get());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void breakerAbreYPermiteSonda() {
        RelojFalso reloj = new RelojFalso();
        CircuitBreaker breaker = new CircuitBreaker(2, 1000, reloj);
        assertTrue(breaker.permite());
        breaker.fallo();
        breaker.fallo();
        assertEquals(CircuitBreaker.Estado.ABIERTO, breaker.estado());
        assertTrue(!breaker.permite());
        reloj.ahora = 1000;
        assertTrue(breaker.permite());
        assertEquals(CircuitBreaker.Estado.SEMIABIERTO, breaker.estado());
        breaker.exito();
        assertEquals(CircuitBreaker.Estado.CERRADO, breaker.estado());
    }

    private static String get(Future<String> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class RelojFalso implements Reloj {
        private long ahora;

        @Override
        public long ahoraMs() {
            return ahora;
        }
    }
}
