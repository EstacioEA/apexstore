package co.edu.icesi.apexstore.cliente;

import ApexStore.Checkout.GestionarCompraHttpPrx;
import ApexStore.Comun.LineaCompra;
import ApexStore.Comun.ResultadoCompra;
import ApexStore.Comun.SolicitudCompra;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Util;
import java.util.Map;

/** Cliente CLI para smoke e idempotencia sobre ICE. */
public final class ClienteApp {
    private ClienteApp() {
    }

    public static void main(String[] args) {
        String escenario = args.length == 0 ? "smoke" : args[0];
        String config = "config/cliente.properties";
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                config = arg.substring("--config=".length());
            }
        }
        try (Communicator communicator = Util.initialize(new String[0], config)) {
            GestionarCompraHttpPrx checkout = GestionarCompraHttpPrx.uncheckedCast(
                    communicator.propertyToProxy("Cliente.Checkout.Proxy"));
            if ("idempotencia".equalsIgnoreCase(escenario)) {
                SolicitudCompra solicitud = solicitud("cliente", "misma-clave", "STRIPE");
                CanalWeb canal = new CanalWeb(checkout);
                ResultadoCompra a = canal.comprar(solicitud);
                ResultadoCompra b = canal.comprar(solicitud);
                if (!a.ordenId.equals(b.ordenId)) {
                    throw new IllegalStateException("La idempotencia devolvió órdenes distintas");
                }
                ReporteConsola.imprimir(escenario, "ordenId=" + a.ordenId);
            } else {
                ResultadoCompra stripe = new CanalWeb(checkout).comprar(
                        solicitud("cliente-web", "web-" + System.nanoTime(), "STRIPE"));
                ResultadoCompra cripto = new CanalMovil(checkout).comprar(
                        solicitud("cliente-movil", "movil-" + System.nanoTime(), "CRIPTO"));
                ReporteConsola.imprimir(escenario,
                        "stripe=" + stripe.estado + " cripto=" + cripto.estado);
            }
        }
    }

    private static SolicitudCompra solicitud(String cliente, String clave, String medio) {
        String moneda = "STRIPE".equals(medio) ? "USD" : "BTC";
        Map<String, String> datos = "STRIPE".equals(medio)
                ? Map.of("tokenTarjeta", "tok_demo", "cvcSeguridad", "123")
                : Map.of("direccionWallet", "wallet_demo", "redBlockchain", "testnet");
        return new SolicitudCompra(cliente, clave, moneda, medio,
                new LineaCompra[]{new LineaCompra("SKU-DEMO", 1, 100)}, datos);
    }
}
