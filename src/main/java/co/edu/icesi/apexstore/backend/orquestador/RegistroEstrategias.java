package co.edu.icesi.apexstore.backend.orquestador;

import ApexStore.Pagos.EstrategiaPagosPrx;
import ApexStore.Comun.MedioPagoNoSoportado;
import com.zeroc.Ice.Communicator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registro configurable de estrategias por código neutro. */
public final class RegistroEstrategias {
    private final Map<String, EntradaEstrategia> entradas = new ConcurrentHashMap<>();

    public RegistroEstrategias(Communicator communicator, int ackTimeoutMs,
                               int umbralFallos, long abiertoMs, int maxConcurrentes) {
        var props = communicator.getProperties();
        String medios = props.getProperty("Orquestador.Medios");
        if (medios == null) {
            medios = "";
        }
        for (String medio : medios.split(",")) {
            String codigo = medio.trim();
            if (codigo.isEmpty()) {
                continue;
            }
            String propiedad = props.getProperty("Orquestador.Medio." + codigo + ".Proxy");
            if (propiedad == null) {
                continue;
            }
            EstrategiaPagosPrx proxy = EstrategiaPagosPrx.uncheckedCast(
                    communicator.propertyToProxy("Orquestador.Medio." + codigo + ".Proxy"))
                    .ice_invocationTimeout(ackTimeoutMs);
            entradas.put(codigo, new EntradaEstrategia(proxy,
                    new CircuitBreaker(umbralFallos, abiertoMs),
                    new Bulkhead(maxConcurrentes)));
        }
    }

    public EntradaEstrategia resolver(String codigo) throws MedioPagoNoSoportado {
        EntradaEstrategia entrada = entradas.get(codigo);
        if (entrada == null) {
            throw new MedioPagoNoSoportado("Medio no soportado", codigo);
        }
        return entrada;
    }

    public Map<String, EntradaEstrategia> todas() {
        return Map.copyOf(entradas);
    }
}
