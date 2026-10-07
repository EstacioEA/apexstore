package co.edu.icesi.apexstore.cliente;

import ApexStore.Checkout.GestionarCompraHttpPrx;
import ApexStore.Comun.ResultadoCompra;
import ApexStore.Comun.SolicitudCompra;

/** Canal cliente que simula la WebApp. */
public final class CanalWeb {
    private final GestionarCompraHttpPrx checkout;

    public CanalWeb(GestionarCompraHttpPrx checkout) {
        this.checkout = checkout;
    }

    public ResultadoCompra comprar(SolicitudCompra solicitud) {
        try {
            return checkout.gestionarCompra(solicitud);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falló la compra web", e);
        }
    }
}
