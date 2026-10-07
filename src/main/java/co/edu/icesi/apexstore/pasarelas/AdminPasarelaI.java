package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Pruebas.ConfigPasarela;
import com.zeroc.Ice.Current;

/** Servant administrativo separado del servant de cobros por las reglas de despacho ICE. */
public final class AdminPasarelaI implements ApexStore.Pruebas.AdminPasarela {
    private final PasarelaSimuladaBase pasarela;

    public AdminPasarelaI(PasarelaSimuladaBase pasarela) {
        this.pasarela = pasarela;
    }

    @Override
    public void configurar(ConfigPasarela cfg, Current current) {
        pasarela.configurar(cfg, current);
    }

    @Override
    public ConfigPasarela configuracion(Current current) {
        return pasarela.configuracion(current);
    }

    @Override
    public long cobrosRecibidos(Current current) {
        return pasarela.cobrosRecibidos(current);
    }

    @Override
    public long callbacksEnviados(Current current) {
        return pasarela.callbacksEnviados(current);
    }

    @Override
    public void reiniciarContadores(Current current) {
        pasarela.reiniciarContadores(current);
    }
}
