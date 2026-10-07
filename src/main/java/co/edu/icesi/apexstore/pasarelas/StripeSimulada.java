package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Comun.SolicitudInvalida;
import ApexStore.Comun.SolicitudCobro;
import java.util.concurrent.ScheduledExecutorService;

/** Simulación de cobros STRIPE sin red externa. */
public final class StripeSimulada extends PasarelaSimuladaBase {
    public StripeSimulada(String codigo, ConfigSimulacion cfg, NotificadorCallback notif,
                          ScheduledExecutorService scheduler) {
        super(codigo, cfg, notif, scheduler);
    }

    @Override
    protected void validarEspecifico(SolicitudCobro solicitud) throws SolicitudInvalida {
        if (!("USD".equals(solicitud.moneda) || "EUR".equals(solicitud.moneda))
                || solicitud.datosPago == null
                || vacio(solicitud.datosPago.get("tokenTarjeta"))
                || vacio(solicitud.datosPago.get("cvcSeguridad"))) {
            throw new SolicitudInvalida("STRIPE requiere tokenTarjeta y cvcSeguridad en USD/EUR");
        }
    }

    @Override
    protected String referencia(SolicitudCobro solicitud) {
        return "ch_" + solicitud.transaccionId;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
