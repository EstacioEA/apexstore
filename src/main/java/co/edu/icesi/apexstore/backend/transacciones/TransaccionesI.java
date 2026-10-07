package co.edu.icesi.apexstore.backend.transacciones;

import ApexStore.Comun.EstadoTransaccion;
import ApexStore.Comun.PagoException;
import ApexStore.Comun.ResultadoPago;
import ApexStore.Comun.Transaccion;
import ApexStore.Comun.TransaccionDesconocida;
import ApexStore.Comun.TransaccionDuplicada;
import ApexStore.Persistencia.PersistirTransaccionPrx;
import com.zeroc.Ice.Current;
import java.util.concurrent.atomic.AtomicLong;

/** Repository del backend que delega toda persistencia al nodo DB. */
public final class TransaccionesI implements ApexStore.Persistencia.RegistrarTransaccion {
    private final PersistirTransaccionPrx baseDatos;
    private final AtomicLong conflictosTardios = new AtomicLong();

    public TransaccionesI(PersistirTransaccionPrx baseDatos) {
        this.baseDatos = baseDatos;
    }

    @Override
    public void registrarPendiente(Transaccion tx, Current current)
            throws TransaccionDuplicada, PagoException {
        try {
            baseDatos.insertar(tx);
        } catch (PagoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PagoException("No se pudo registrar la transacción: " + e.getMessage());
        }
    }

    @Override
    public boolean registrarResultado(ResultadoPago resultado, Current current)
            throws TransaccionDesconocida, PagoException {
        try {
            boolean cambio = baseDatos.transicionar(resultado.transaccionId, EstadoTransaccion.TxPendiente,
                    resultado.estado, resultado.referenciaExterna, resultado.motivo);
            if (cambio) {
                return true;
            }
            Transaccion actual = baseDatos.obtener(resultado.transaccionId);
            if (actual.estado == resultado.estado) {
                baseDatos.auditar(resultado.transaccionId, "DUPLICADO_IGNORADO",
                        "Resultado repetido: " + resultado.estado);
            } else {
                baseDatos.auditar(resultado.transaccionId, "CONFLICTO_TARDIO",
                        "Estado actual " + actual.estado + ", resultado " + resultado.estado);
                conflictosTardios.incrementAndGet();
            }
            return false;
        } catch (TransaccionDesconocida e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PagoException("No se pudo registrar resultado: " + e.getMessage());
        }
    }

    @Override
    public Transaccion obtener(String transaccionId, Current current)
            throws TransaccionDesconocida {
        return baseDatos.obtener(transaccionId);
    }

    @Override
    public Transaccion[] expirarPendientes(long antesDeMs, Current current) {
        return baseDatos.expirarPendientes(antesDeMs);
    }

    public long conflictosTardios() {
        return conflictosTardios.get();
    }
}
