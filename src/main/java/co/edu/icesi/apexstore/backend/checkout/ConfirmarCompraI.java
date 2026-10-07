package co.edu.icesi.apexstore.backend.checkout;

import ApexStore.Checkout.ConfirmarCompra;
import ApexStore.Comun.OrdenDesconocida;
import ApexStore.Comun.ResultadoPago;
import com.zeroc.Ice.Current;

/** Servant ICE del callback de confirmación de órdenes. */
public final class ConfirmarCompraI implements ConfirmarCompra {
    private final ServicioCheckoutI servicio;

    public ConfirmarCompraI(ServicioCheckoutI servicio) {
        this.servicio = servicio;
    }

    @Override
    public void confirmar(ResultadoPago resultado, Current current) throws OrdenDesconocida {
        servicio.confirmar(resultado, current);
    }
}
