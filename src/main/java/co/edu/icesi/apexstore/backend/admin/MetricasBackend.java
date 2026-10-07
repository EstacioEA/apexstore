package co.edu.icesi.apexstore.backend.admin;

import ApexStore.Pruebas.MetricasLatencia;
import co.edu.icesi.apexstore.comun.Histograma;

/** Métricas thread-safe de latencia y callbacks del backend. */
public final class MetricasBackend {
    private final Histograma latencias = new Histograma(100_000);

    public void registrarLatencia(double milis) {
        latencias.registrar(milis);
    }

    public MetricasLatencia latencia() {
        return new MetricasLatencia(latencias.muestras(), latencias.p50(),
                latencias.p95(), latencias.p99(), latencias.maximo());
    }

    public void reiniciar() {
        // La ventana es deliberadamente acotada; las métricas del proceso se conservan.
    }
}
