package co.edu.icesi.apexstore.backend.admin;

import co.edu.icesi.apexstore.backend.checkout.ServicioCheckoutI;
import co.edu.icesi.apexstore.backend.orquestador.CircuitBreaker;
import co.edu.icesi.apexstore.backend.orquestador.RegistroEstrategias;
import co.edu.icesi.apexstore.backend.transacciones.TransaccionesI;
import com.zeroc.Ice.Current;
import java.util.HashMap;
import java.util.Map;

/** Servant administrativo del backend para métricas y circuitos. */
public final class AdminBackendI implements ApexStore.Pruebas.AdminBackend {
    private final MetricasBackend metricas;
    private final RegistroEstrategias registro;
    private final TransaccionesI transacciones;
    private final ServicioCheckoutI checkout;

    public AdminBackendI(MetricasBackend metricas, RegistroEstrategias registro,
                         TransaccionesI transacciones, ServicioCheckoutI checkout) {
        this.metricas = metricas;
        this.registro = registro;
        this.transacciones = transacciones;
        this.checkout = checkout;
    }

    @Override
    public ApexStore.Pruebas.MetricasLatencia latenciaOrquestacion(Current current) {
        return metricas.latencia();
    }

    @Override
    public Map<String, String> obtenerEstadoCircuitos(Current current) {
        Map<String, String> estados = new HashMap<>();
        registro.todas().forEach((codigo, entrada) ->
                estados.put(codigo, entrada.breaker().estado().name()));
        return estados;
    }

    @Override
    public long conflictosTardios(Current current) {
        return transacciones.conflictosTardios();
    }

    @Override
    public long confirmacionesAplicadas(Current current) {
        return checkout.confirmacionesAplicadas();
    }

    @Override
    public void reiniciarMetricas(Current current) {
        metricas.reiniciar();
    }
}
