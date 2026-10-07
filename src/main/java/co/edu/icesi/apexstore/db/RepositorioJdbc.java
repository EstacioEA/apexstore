package co.edu.icesi.apexstore.db;

import ApexStore.Comun.EventoAuditoria;
import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.Transaccion;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Repositorio JDBC con CAS transaccional y auditoría atómica. */
public final class RepositorioJdbc implements AutoCloseable {
    private final String url;
    private final String usuario;
    private final String clave;

    public RepositorioJdbc(String url) throws SQLException {
        this(url, "", "");
    }

    public RepositorioJdbc(String url, String usuario, String clave) throws SQLException {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
        try (Connection conexion = abrir()) {
            EsquemaSql.crear(conexion);
        }
    }

    private Connection abrir() throws SQLException {
        Connection conexion = DriverManager.getConnection(url, usuario, clave);
        conexion.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return conexion;
    }

    public void insertar(Transaccion tx) throws SQLException {
        try (Connection conexion = abrir()) {
            conexion.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conexion.prepareStatement("""
                        INSERT INTO transacciones
                        (transaccion_id, orden_id, codigo_medio_pago, monto_minor, moneda, estado,
                         referencia_externa, motivo, creada_ms, actualizada_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    bind(ps, tx);
                    ps.executeUpdate();
                }
                auditar(conexion, tx.transaccionId, "CREADA", null, tx.estado, "Inserción inicial", tx.actualizadaMs);
                conexion.commit();
            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            } finally {
                conexion.setAutoCommit(true);
            }
        }
    }

    public boolean transicionar(String transaccionId, EstadoTransaccion esperado,
                                EstadoTransaccion nuevo, String referencia, String motivo)
            throws SQLException {
        try (Connection conexion = abrir()) {
            conexion.setAutoCommit(false);
            try {
                Transaccion actual = obtener(conexion, transaccionId);
                if (actual == null) {
                    conexion.rollback();
                    throw new SQLException("TRANSACCION_DESCONOCIDA:" + transaccionId);
                }
                if (actual.estado != esperado) {
                    conexion.rollback();
                    return false;
                }
                long ahora = System.currentTimeMillis();
                int filas;
                try (PreparedStatement ps = conexion.prepareStatement("""
                        UPDATE transacciones
                           SET estado=?, referencia_externa=?, motivo=?, actualizada_ms=?
                         WHERE transaccion_id=? AND estado=?
                        """)) {
                    ps.setString(1, nuevo.name());
                    ps.setString(2, referencia);
                    ps.setString(3, motivo);
                    ps.setLong(4, ahora);
                    ps.setString(5, transaccionId);
                    ps.setString(6, esperado.name());
                    filas = ps.executeUpdate();
                }
                if (filas != 1) {
                    conexion.rollback();
                    return false;
                }
                auditar(conexion, transaccionId, "TRANSICION", esperado, nuevo, motivo, ahora);
                conexion.commit();
                return true;
            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            } finally {
                conexion.setAutoCommit(true);
            }
        }
    }

    public void auditar(String transaccionId, String evento, String detalle) throws SQLException {
        try (Connection conexion = abrir()) {
            conexion.setAutoCommit(false);
            try {
                if (obtener(conexion, transaccionId) == null) {
                    throw new SQLException("TRANSACCION_DESCONOCIDA:" + transaccionId);
                }
                auditar(conexion, transaccionId, evento, null, null, detalle, System.currentTimeMillis());
                conexion.commit();
            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            }
        }
    }

    public Transaccion obtener(String transaccionId) throws SQLException {
        try (Connection conexion = abrir()) {
            return obtener(conexion, transaccionId);
        }
    }

    public Transaccion obtenerPorOrden(String ordenId) throws SQLException {
        try (Connection conexion = abrir();
             PreparedStatement ps = conexion.prepareStatement(
                     "SELECT * FROM transacciones WHERE orden_id=?")) {
            ps.setString(1, ordenId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public EventoAuditoria[] auditoria(String transaccionId) throws SQLException {
        List<EventoAuditoria> eventos = new ArrayList<>();
        try (Connection conexion = abrir();
             PreparedStatement ps = conexion.prepareStatement(
                     "SELECT * FROM auditoria WHERE transaccion_id=? ORDER BY id")) {
            ps.setString(1, transaccionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    eventos.add(new EventoAuditoria(rs.getLong("id"), rs.getString("transaccion_id"),
                            rs.getString("evento"), nullToEmpty(rs.getString("estado_anterior")),
                            nullToEmpty(rs.getString("estado_nuevo")), nullToEmpty(rs.getString("detalle")),
                            rs.getLong("marca_tiempo_ms")));
                }
            }
        }
        return eventos.toArray(new EventoAuditoria[0]);
    }

    public Map<String, Long> contarPorEstado() throws SQLException {
        Map<String, Long> conteo = new HashMap<>();
        try (Connection conexion = abrir();
             Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT estado, COUNT(*) cantidad FROM transacciones GROUP BY estado")) {
            while (rs.next()) {
                conteo.put(rs.getString("estado"), rs.getLong("cantidad"));
            }
        }
        return conteo;
    }

    public Transaccion[] expirarPendientes(long antesDeMs) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection conexion = abrir();
             PreparedStatement ps = conexion.prepareStatement(
                     "SELECT transaccion_id FROM transacciones WHERE estado=? AND creada_ms < ?")) {
            ps.setString(1, EstadoTransaccion.TxPendiente.name());
            ps.setLong(2, antesDeMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
        }
        List<Transaccion> expiradas = new ArrayList<>();
        for (String id : ids) {
            if (transicionar(id, EstadoTransaccion.TxPendiente, EstadoTransaccion.TxExpirada,
                    "", "EXPIRADA")) {
                expiradas.add(obtener(id));
            }
        }
        return expiradas.toArray(new Transaccion[0]);
    }

    private Transaccion obtener(Connection conexion, String id) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT * FROM transacciones WHERE transaccion_id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    private static Transaccion mapear(ResultSet rs) throws SQLException {
        return new Transaccion(rs.getString("transaccion_id"), rs.getString("orden_id"),
                rs.getString("codigo_medio_pago"), rs.getLong("monto_minor"), rs.getString("moneda"),
                EstadoTransaccion.valueOf(rs.getString("estado")),
                nullToEmpty(rs.getString("referencia_externa")), nullToEmpty(rs.getString("motivo")),
                rs.getLong("creada_ms"), rs.getLong("actualizada_ms"));
    }

    private static void bind(PreparedStatement ps, Transaccion tx) throws SQLException {
        ps.setString(1, tx.transaccionId);
        ps.setString(2, tx.ordenId);
        ps.setString(3, tx.codigoMedioPago);
        ps.setLong(4, tx.montoMinor);
        ps.setString(5, tx.moneda);
        ps.setString(6, tx.estado.name());
        ps.setString(7, tx.referenciaExterna);
        ps.setString(8, tx.motivo);
        ps.setLong(9, tx.creadaMs);
        ps.setLong(10, tx.actualizadaMs);
    }

    private static void auditar(Connection conexion, String txId, String evento,
                                EstadoTransaccion anterior, EstadoTransaccion nuevo,
                                String detalle, long cuando) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("""
                INSERT INTO auditoria
                (transaccion_id, evento, estado_anterior, estado_nuevo, detalle, marca_tiempo_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, txId);
            ps.setString(2, evento);
            ps.setString(3, anterior == null ? null : anterior.name());
            ps.setString(4, nuevo == null ? null : nuevo.name());
            ps.setString(5, detalle);
            ps.setLong(6, cuando);
            ps.executeUpdate();
        }
    }

    private static String nullToEmpty(String valor) {
        return valor == null ? "" : valor;
    }

    @Override
    public void close() {
    }
}
