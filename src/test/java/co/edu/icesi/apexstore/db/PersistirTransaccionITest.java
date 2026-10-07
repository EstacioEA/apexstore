package co.edu.icesi.apexstore.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.Transaccion;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import org.junit.jupiter.api.Test;

class PersistirTransaccionITest {
    @Test
    void invocaServantPorProxyIce() throws Exception {
        try (Communicator communicator = Util.initialize()) {
            try (RepositorioJdbc repo = new RepositorioJdbc(
                    "jdbc:h2:mem:proxy_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1")) {
                ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("test", "tcp -p 0");
                adapter.add(new PersistirTransaccionI(repo), Util.stringToIdentity("DbTransacciones"));
                adapter.activate();
                ApexStore.Persistencia.PersistirTransaccionPrx proxy =
                        ApexStore.Persistencia.PersistirTransaccionPrx.uncheckedCast(
                                adapter.createProxy(Util.stringToIdentity("DbTransacciones")));
                Transaccion tx = new Transaccion("tx-proxy", "ord-proxy", "STRIPE", 100, "USD",
                        EstadoTransaccion.TxPendiente, "", "", System.currentTimeMillis(),
                        System.currentTimeMillis());
                proxy.insertar(tx);
                assertEquals("ord-proxy", proxy.obtener("tx-proxy").ordenId);
                assertEquals(1, proxy.auditoria("tx-proxy").length);
            }
        }
    }
}
