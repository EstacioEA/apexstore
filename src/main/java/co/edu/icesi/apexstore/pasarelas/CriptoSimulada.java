package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Comun.SolicitudCobro;
import ApexStore.Comun.SolicitudInvalida;
import java.util.concurrent.ScheduledExecutorService;

/** Simulación de confirmación blockchain sin red externa. */
public final class CriptoSimulada extends PasarelaSimuladaBase {
    public CriptoSimulada(String codigo, ConfigSimulacion cfg, NotificadorCallback notif,
                          ScheduledExecutorService scheduler) {
        super(codigo, cfg, notif, scheduler);
    }

    @Override
    protected void validarEspecifico(SolicitudCobro solicitud) throws SolicitudInvalida {
        if (!("BTC".equals(solicitud.moneda) || "USD".equals(solicitud.moneda))
                || solicitud.datosPago == null
                || vacio(solicitud.datosPago.get("direccionWallet"))
                || vacio(solicitud.datosPago.get("redBlockchain"))) {
            throw new SolicitudInvalida("CRIPTO requiere direccionWallet y redBlockchain en BTC/USD");
        }
    }

    @Override
    protected String referencia(SolicitudCobro solicitud) {
        return "0x" + Integer.toHexString(solicitud.transaccionId.hashCode());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
