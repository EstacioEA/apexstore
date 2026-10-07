package co.edu.icesi.apexstore.pasarelas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ApexStore.Comun.AcuseCobro;
import ApexStore.Comun.SolicitudCobro;
import ApexStore.Pruebas.ConfigPasarela;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class BilleteraSimuladaTest {
    @Test
    void extensionUsaContratoExistente() throws Exception {
        try (var scheduler = Executors.newScheduledThreadPool(1)) {
            ConfigSimulacion cfg = new ConfigSimulacion(new ConfigPasarela("SIN_CALLBACK", 0, 0, 0, 0, 0));
            BilleteraSimulada billetera = new BilleteraSimulada("BILLETERA", cfg,
                    new NotificadorCallback(null, scheduler, 1, 1), scheduler);
            AcuseCobro acuse = billetera.iniciarCobro(new SolicitudCobro(
                    "tx-wallet", "ord-wallet", 100, "COP",
                    Map.of("idBilletera", "wallet", "pin", "1234")), new com.zeroc.Ice.Current());
            assertEquals(true, acuse.aceptado);
        }
    }
}
