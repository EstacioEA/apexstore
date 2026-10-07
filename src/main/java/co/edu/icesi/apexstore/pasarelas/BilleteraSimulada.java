package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Comun.SolicitudCobro;
import ApexStore.Comun.SolicitudInvalida;
import java.util.concurrent.ScheduledExecutorService;

/** Extensión de billetera sin cambios en el contrato ni en el backend. */
public final class BilleteraSimulada extends PasarelaSimuladaBase {
    public BilleteraSimulada(String codigo, ConfigSimulacion cfg, NotificadorCallback notif,
                             ScheduledExecutorService scheduler) {
        super(codigo, cfg, notif, scheduler);
    }

    @Override
    protected void validarEspecifico(SolicitudCobro solicitud) throws SolicitudInvalida {
        if (!("COP".equals(solicitud.moneda) || "USD".equals(solicitud.moneda))
                || solicitud.datosPago == null
                || vacio(solicitud.datosPago.get("idBilletera"))
                || vacio(solicitud.datosPago.get("pin"))) {
            throw new SolicitudInvalida("BILLETERA requiere idBilletera y pin en COP/USD");
        }
    }

    @Override
    protected String referencia(SolicitudCobro solicitud) {
        return "wallet_" + solicitud.transaccionId;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
