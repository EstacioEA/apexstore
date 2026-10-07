package co.edu.icesi.apexstore.backend.checkout;

import ApexStore.Checkout.GestionarCompraHttp;
import ApexStore.Comun.MedioPagoNoSoportado;
import ApexStore.Comun.OrdenDesconocida;
import ApexStore.Comun.PagoException;
import ApexStore.Comun.ResultadoCompra;
import ApexStore.Comun.SolicitudCompra;
import ApexStore.Comun.SolicitudInvalida;
import com.zeroc.Ice.Current;

/** Servant ICE del contrato de gestión de compras. */
public final class GestionarCompraI implements GestionarCompraHttp {
    private final ServicioCheckoutI servicio;

    public GestionarCompraI(ServicioCheckoutI servicio) {
        this.servicio = servicio;
    }

    @Override
    public ResultadoCompra gestionarCompra(SolicitudCompra solicitud, Current current)
            throws SolicitudInvalida, MedioPagoNoSoportado, PagoException {
        return servicio.gestionarCompra(solicitud, current);
    }

    @Override
    public ResultadoCompra consultarEstado(String ordenId, Current current) throws OrdenDesconocida {
        return servicio.consultarEstado(ordenId, current);
    }
}
