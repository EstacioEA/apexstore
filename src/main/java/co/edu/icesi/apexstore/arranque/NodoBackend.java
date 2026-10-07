package co.edu.icesi.apexstore.arranque;

import ApexStore.Checkout.ConfirmarCompraPrx;
import ApexStore.Pagos.IniciarPagoOrdenPrx;
import ApexStore.Persistencia.PersistirTransaccionPrx;
import ApexStore.Persistencia.RegistrarTransaccionPrx;
import co.edu.icesi.apexstore.backend.admin.AdminBackendI;
import co.edu.icesi.apexstore.backend.admin.MetricasBackend;
import co.edu.icesi.apexstore.backend.checkout.ConfirmarCompraI;
import co.edu.icesi.apexstore.backend.checkout.GestionarCompraI;
import co.edu.icesi.apexstore.backend.checkout.RepositorioOrdenes;
import co.edu.icesi.apexstore.backend.checkout.ServicioCheckoutI;
import co.edu.icesi.apexstore.backend.orquestador.OrquestadorPagosI;
import co.edu.icesi.apexstore.backend.orquestador.RegistroEstrategias;
import co.edu.icesi.apexstore.backend.receptor.ReceptorResultadosPagosI;
import co.edu.icesi.apexstore.backend.transacciones.SweeperExpiracion;
import co.edu.icesi.apexstore.backend.transacciones.TransaccionesI;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Arranque del nodo backend y cableado mediante proxies ICE. */
public final class NodoBackend {
    private NodoBackend() {
    }

    public static void ejecutar(String[] args) {
        String archivo = argumentoConfig(args, "config/backend.properties");
        try (Communicator communicator = Util.initialize(new String[0], archivo);
             ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2)) {
            var props = communicator.getProperties();
            PersistirTransaccionPrx db = PersistirTransaccionPrx.uncheckedCast(
                    communicator.propertyToProxy("Transacciones.Persistencia.Proxy"));
            RegistrarTransaccionPrx transaccionesProxy = RegistrarTransaccionPrx.uncheckedCast(
                    communicator.propertyToProxy("Orquestador.Transacciones.Proxy"));
            ConfirmarCompraPrx confirmarProxy = ConfirmarCompraPrx.uncheckedCast(
                    communicator.propertyToProxy("Receptor.Checkout.Proxy"));
            int ack = entero(props, "Orquestador.AckTimeoutMs", 200);
            int umbral = entero(props, "Orquestador.Breaker.UmbralFallos", 5);
            long abierto = entero(props, "Orquestador.Breaker.AbiertoMs", 3000);
            int max = entero(props, "Orquestador.Bulkhead.MaxConcurrentes", 64);
            RegistroEstrategias registro = new RegistroEstrategias(communicator, ack, umbral, abierto, max);
            TransaccionesI transacciones = new TransaccionesI(db);
            MetricasBackend metricas = new MetricasBackend();
            OrquestadorPagosI orquestador = new OrquestadorPagosI(registro, transaccionesProxy, metricas);
            ServicioCheckoutI checkout = new ServicioCheckoutI(new RepositorioOrdenes(),
                    IniciarPagoOrdenPrx.uncheckedCast(communicator.propertyToProxy("Checkout.Orquestador.Proxy")));
            ReceptorResultadosPagosI receptor = new ReceptorResultadosPagosI(transaccionesProxy, confirmarProxy);
            ObjectAdapter adapter = communicator.createObjectAdapter("Backend");
            adapter.add(new GestionarCompraI(checkout), Util.stringToIdentity("ServicioCheckout"));
            adapter.add(new ConfirmarCompraI(checkout), Util.stringToIdentity("ConfirmarCompra"));
            adapter.add(orquestador, Util.stringToIdentity("OrquestadorPagos"));
            adapter.add(receptor, Util.stringToIdentity("ReceptorResultadosPagos"));
            adapter.add(transacciones, Util.stringToIdentity("Transacciones"));
            adapter.add(new AdminBackendI(metricas, registro, transacciones, checkout),
                    Util.stringToIdentity("AdminBackend"));
            adapter.activate();
            long expiracion = entero(props, "Expiracion.TxPendienteMs", 20_000);
            long periodo = entero(props, "Expiracion.PeriodoMs", 2_000);
            try (SweeperExpiracion sweeper = new SweeperExpiracion(transaccionesProxy,
                    confirmarProxy, scheduler, expiracion, periodo)) {
                communicator.waitForShutdown();
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo iniciar el nodo backend", e);
        }
    }

    private static int entero(com.zeroc.Ice.Properties props, String clave, int defecto) {
        String valor = props.getProperty(clave);
        return valor == null || valor.isBlank() ? defecto : Integer.parseInt(valor);
    }

    private static String argumentoConfig(String[] args, String defecto) {
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                return arg.substring("--config=".length());
            }
        }
        return defecto;
    }
}
