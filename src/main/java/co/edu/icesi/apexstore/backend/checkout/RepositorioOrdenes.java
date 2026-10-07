package co.edu.icesi.apexstore.backend.checkout;

import ApexStore.Comun.EstadoOrden;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Almacén concurrente de órdenes, con índice de idempotencia. */
public final class RepositorioOrdenes {
    public static final class Orden {
        public final String ordenId;
        public final String claveIdempotencia;
        public final long montoMinor;
        public final String moneda;
        public final String codigoMedioPago;
        public volatile EstadoOrden estado;
        public volatile String transaccionId;
        public volatile String mensaje;

        private Orden(String ordenId, String claveIdempotencia, long montoMinor,
                      String moneda, String codigoMedioPago) {
            this.ordenId = ordenId;
            this.claveIdempotencia = claveIdempotencia;
            this.montoMinor = montoMinor;
            this.moneda = moneda;
            this.codigoMedioPago = codigoMedioPago;
            this.estado = EstadoOrden.OrdenPendientePago;
            this.transaccionId = "";
            this.mensaje = "";
        }
    }

    private final ConcurrentMap<String, Orden> porId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> porClave = new ConcurrentHashMap<>();

    public Orden crearSiNoExiste(String clave, long monto, String moneda, String medio, String id) {
        Orden nueva = new Orden(id, clave, monto, moneda, medio);
        porId.put(id, nueva);
        String existente = porClave.putIfAbsent(clave, id);
        if (existente != null) {
            porId.remove(id);
            return porId.get(existente);
        }
        return nueva;
    }

    public Orden porId(String ordenId) {
        return porId.get(ordenId);
    }

    public Orden porClave(String clave) {
        String id = porClave.get(clave);
        return id == null ? null : porId.get(id);
    }

    public Orden porTransaccion(String transaccionId) {
        return porId.values().stream()
                .filter(orden -> transaccionId.equals(orden.transaccionId))
                .findFirst().orElse(null);
    }
}
