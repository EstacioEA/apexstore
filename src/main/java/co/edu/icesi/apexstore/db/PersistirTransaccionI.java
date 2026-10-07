package co.edu.icesi.apexstore.db;

import ApexStore.Comun.EventoAuditoria;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.PagoException;
import ApexStore.Comun.Transaccion;
import ApexStore.Comun.TransaccionDesconocida;
import ApexStore.Comun.TransaccionDuplicada;
import com.zeroc.Ice.Current;
import java.sql.SQLException;
import java.util.Map;

/** Servant ICE que traduce el repositorio JDBC al contrato de persistencia. */
public final class PersistirTransaccionI implements ApexStore.Persistencia.PersistirTransaccion {
    private final RepositorioJdbc repositorio;

    public PersistirTransaccionI(RepositorioJdbc repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void insertar(Transaccion tx, Current current) throws TransaccionDuplicada, PagoException {
        try {
            repositorio.insertar(tx);
        } catch (SQLException e) {
            if (esDuplicado(e)) {
                throw new TransaccionDuplicada("Orden duplicada", tx.ordenId);
            }
            throw new PagoException(e.getMessage());
        }
    }

    @Override
    public boolean transicionar(String transaccionId, EstadoTransaccion esperado,
                                EstadoTransaccion nuevo, String referenciaExterna,
                                String motivo, Current current) throws TransaccionDesconocida {
        try {
            return repositorio.transicionar(transaccionId, esperado, nuevo, referenciaExterna, motivo);
        } catch (SQLException e) {
            if (esDesconocida(e)) {
                throw new TransaccionDesconocida("Transacción desconocida", transaccionId);
            }
            throw new IllegalStateException("Error JDBC al transicionar " + transaccionId, e);
        }
    }

    @Override
    public void auditar(String transaccionId, String evento, String detalle, Current current) {
        try {
            repositorio.auditar(transaccionId, evento, detalle);
        } catch (SQLException e) {
            throw new IllegalStateException("Error JDBC de auditoria " + transaccionId, e);
        }
    }

    @Override
    public Transaccion obtener(String transaccionId, Current current) throws TransaccionDesconocida {
        try {
            Transaccion tx = repositorio.obtener(transaccionId);
            if (tx == null) {
                throw new TransaccionDesconocida("Transacción desconocida", transaccionId);
            }
            return tx;
        } catch (SQLException e) {
            throw new IllegalStateException("Error JDBC al obtener " + transaccionId, e);
        }
    }

    @Override
    public Transaccion obtenerPorOrden(String ordenId, Current current) throws TransaccionDesconocida {
        try {
            Transaccion tx = repositorio.obtenerPorOrden(ordenId);
            if (tx == null) {
                throw new TransaccionDesconocida("Orden desconocida", ordenId);
            }
            return tx;
        } catch (SQLException e) {
            throw new IllegalStateException("Error JDBC al buscar orden " + ordenId, e);
        }
    }

    @Override
    public EventoAuditoria[] auditoria(String transaccionId, Current current) {
        try {
            return repositorio.auditoria(transaccionId);
        } catch (SQLException e) {
            throw new IllegalStateException("Error JDBC de auditoria " + transaccionId, e);
        }
    }

    @Override
    public Map<String, Long> contarPorEstado(Current current) {
        try {
            return repositorio.contarPorEstado();
        } catch (SQLException e) {
            throw new IllegalStateException("Error JDBC de conteo", e);
        }
    }

    @Override
    public Transaccion[] expirarPendientes(long antesDeMs, Current current) {
        try {
            return repositorio.expirarPendientes(antesDeMs);
        } catch (SQLException e) {
            throw new IllegalStateException("Error JDBC de expiracion", e);
        }
    }

    private static boolean esDuplicado(SQLException e) {
        return e.getSQLState() != null && (e.getSQLState().startsWith("23") || e.getMessage().contains("Unique"));
    }

    private static boolean esDesconocida(SQLException e) {
        return e.getMessage() != null && e.getMessage().contains("TRANSACCION_DESCONOCIDA");
    }
}
