package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Comun.SolicitudCobro;
import ApexStore.Comun.SolicitudInvalida;
import java.util.concurrent.ScheduledExecutorService;

/** Simulación de débito PSE sin conexión bancaria. */
public final class PseSimulada extends PasarelaSimuladaBase {
    public PseSimulada(String codigo, ConfigSimulacion cfg, NotificadorCallback notif,
                       ScheduledExecutorService scheduler) {
        super(codigo, cfg, notif, scheduler);
    }

    @Override
    protected void validarEspecifico(SolicitudCobro solicitud) throws SolicitudInvalida {
        if (!"COP".equals(solicitud.moneda) || solicitud.datosPago == null
                || vacio(solicitud.datosPago.get("codigoBanco"))
                || vacio(solicitud.datosPago.get("tipoDoc"))
                || vacio(solicitud.datosPago.get("numCuenta"))) {
            throw new SolicitudInvalida("PSE requiere codigoBanco, tipoDoc y numCuenta en COP");
        }
    }

    @Override
    protected String referencia(SolicitudCobro solicitud) {
        return "pse_" + solicitud.transaccionId;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
