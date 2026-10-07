package co.edu.icesi.apexstore.pasarelas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ApexStore.Comun.AcuseCobro;
import ApexStore.Comun.ResultadoPago;
import ApexStore.Comun.SolicitudCobro;
import ApexStore.Comun.SolicitudInvalida;
import ApexStore.Comun.ServicioNoDisponible;
import ApexStore.Pagos.NotificarResultadoPagoPrx;
import ApexStore.Pruebas.ConfigPasarela;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PasarelasTest {
    @Test
    void normalDuplicadoYValidacion() throws Exception {
        try (Communicator communicator = Util.initialize();
             ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2)) {
            ReceptorFalso receptor = new ReceptorFalso(1);
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("receptor", "tcp -p 0");
            adapter.add(receptor, Util.stringToIdentity("Receptor"));
            adapter.activate();
            NotificarResultadoPagoPrx proxy = NotificarResultadoPagoPrx.uncheckedCast(
                    adapter.createProxy(Util.stringToIdentity("Receptor")));
            ConfigSimulacion cfg = new ConfigSimulacion(new ConfigPasarela(
                    "NORMAL", 0, 0, 0, 0, 0));
            StripeSimulada stripe = new StripeSimulada("STRIPE", cfg,
                    new NotificadorCallback(proxy, scheduler, 5, 1), scheduler);
            SolicitudCobro solicitud = new SolicitudCobro("tx-1", "ord-1", 100, "USD",
                    Map.of("tokenTarjeta", "tok", "cvcSeguridad", "123"));
            AcuseCobro primero = stripe.iniciarCobro(solicitud, new Current());
            AcuseCobro segundo = stripe.iniciarCobro(solicitud, new Current());
            assertEquals(primero.referenciaExterna, segundo.referenciaExterna);
            assertEquals(1, stripe.cobrosRecibidos(new Current()));
            assertTrue(receptor.latch.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void modosCallbackDuplicadoYSinCallback() throws Exception {
        try (Communicator communicator = Util.initialize();
             ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2)) {
            ReceptorFalso receptor = new ReceptorFalso(2);
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("receptor", "tcp -p 0");
            adapter.add(receptor, Util.stringToIdentity("Receptor"));
            adapter.activate();
            NotificarResultadoPagoPrx proxy = NotificarResultadoPagoPrx.uncheckedCast(
                    adapter.createProxy(Util.stringToIdentity("Receptor")));
            ConfigSimulacion cfg = new ConfigSimulacion(new ConfigPasarela(
                    "CALLBACK_DUPLICADO", 0, 0, 0, 0, 0));
            StripeSimulada stripe = new StripeSimulada("STRIPE", cfg,
                    new NotificadorCallback(proxy, scheduler, 5, 1), scheduler);
            stripe.iniciarCobro(new SolicitudCobro("tx-2", "ord-2", 100, "USD",
                    Map.of("tokenTarjeta", "tok", "cvcSeguridad", "123")), new Current());
            assertTrue(receptor.latch.await(2, TimeUnit.SECONDS));
            assertEquals(2, receptor.recibidos);
            cfg.configurar(new ConfigPasarela("SIN_CALLBACK", 0, 0, 0, 0, 0));
            stripe.iniciarCobro(new SolicitudCobro("tx-3", "ord-3", 100, "USD",
                    Map.of("tokenTarjeta", "tok", "cvcSeguridad", "123")), new Current());
            Thread.sleep(50);
            assertEquals(2, receptor.recibidos);
        }
    }

    @Test
    void rechazaSolicitudYCaida() throws Exception {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            ConfigSimulacion cfg = new ConfigSimulacion(new ConfigPasarela("NORMAL", 0, 0, 0, 0, 0));
            NotificadorCallback noop = new NotificadorCallback(
                    null,
                    scheduler, 1, 1);
            StripeSimulada stripe = new StripeSimulada("STRIPE", cfg, noop, scheduler);
            assertThrows(SolicitudInvalida.class, () -> stripe.iniciarCobro(
                    new SolicitudCobro("tx", "ord", 1, "COP", Map.of()), new Current()));
            cfg.configurar(new ConfigPasarela("CAIDA", 0, 0, 0, 0, 0));
            assertThrows(ServicioNoDisponible.class, () -> stripe.iniciarCobro(
                    new SolicitudCobro("tx-2", "ord-2", 1, "USD",
                            Map.of("tokenTarjeta", "tok", "cvcSeguridad", "123")), new Current()));
        }
    }

    private static final class ReceptorFalso implements ApexStore.Pagos.NotificarResultadoPago {
        private final CountDownLatch latch;
        private volatile int recibidos;

        private ReceptorFalso(int esperados) {
            latch = new CountDownLatch(esperados);
        }

        @Override
        public void notificar(ResultadoPago resultado, Current current) {
            recibidos++;
            latch.countDown();
        }
    }
}
