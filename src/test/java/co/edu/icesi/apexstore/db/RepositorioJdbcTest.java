package co.edu.icesi.apexstore.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.Transaccion;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RepositorioJdbcTest {
    @Test
    void insertaAuditaYRechazaOrdenDuplicada() throws Exception {
        try (RepositorioJdbc repo = nuevoRepositorio()) {
            repo.insertar(tx("tx-1", "ord-1", System.currentTimeMillis()));
            assertNotNull(repo.obtener("tx-1"));
            assertEquals(1, repo.auditoria("tx-1").length);
            assertThrows(SQLException.class, () -> repo.insertar(tx("tx-2", "ord-1", System.currentTimeMillis())));
        }
    }

    @Test
    void soloUnaTransicionConcurrenteAplica() throws Exception {
        try (RepositorioJdbc repo = nuevoRepositorio()) {
            repo.insertar(tx("tx-1", "ord-1", System.currentTimeMillis()));
            ExecutorService pool = Executors.newFixedThreadPool(32);
            try {
                List<Callable<Boolean>> tareas = new ArrayList<>();
                for (int i = 0; i < 32; i++) {
                    tareas.add(() -> repo.transicionar("tx-1", EstadoTransaccion.TxPendiente,
                            EstadoTransaccion.TxAprobada, "ref", "ok"));
                }
                int aplicadas = 0;
                for (Future<Boolean> resultado : pool.invokeAll(tareas)) {
                    if (resultado.get()) {
                        aplicadas++;
                    }
                }
                assertEquals(1, aplicadas);
                assertEquals(2, repo.auditoria("tx-1").length);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void transicionDesconocidaFallaSinFila() throws Exception {
        try (RepositorioJdbc repo = nuevoRepositorio()) {
            SQLException error = assertThrows(SQLException.class,
                    () -> repo.transicionar("inexistente", EstadoTransaccion.TxPendiente,
                            EstadoTransaccion.TxFallida, "", "x"));
            assertTrue(error.getMessage().contains("TRANSACCION_DESCONOCIDA"));
        }
    }

    private static RepositorioJdbc nuevoRepositorio() throws SQLException {
        return new RepositorioJdbc("jdbc:h2:mem:db_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    private static Transaccion tx(String txId, String ordenId, long ahora) {
        return new Transaccion(txId, ordenId, "STRIPE", 100, "USD",
                EstadoTransaccion.TxPendiente, "", "", ahora, ahora);
    }
}
