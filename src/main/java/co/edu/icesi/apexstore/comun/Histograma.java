package co.edu.icesi.apexstore.comun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Histograma thread-safe con percentiles sobre una ventana acotada. */
public final class Histograma {
    private final int capacidad;
    private final List<Double> muestras = new ArrayList<>();
    private final AtomicLong total = new AtomicLong();

    public Histograma(int capacidad) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser positiva");
        }
        this.capacidad = capacidad;
    }

    public synchronized void registrar(double valor) {
        if (!Double.isFinite(valor) || valor < 0) {
            throw new IllegalArgumentException("La muestra debe ser finita y no negativa");
        }
        total.incrementAndGet();
        if (muestras.size() == capacidad) {
            muestras.remove(0);
        }
        muestras.add(valor);
    }

    public long muestras() {
        return total.get();
    }

    public synchronized double percentil(double p) {
        if (p < 0 || p > 100) {
            throw new IllegalArgumentException("El percentil debe estar entre 0 y 100");
        }
        if (muestras.isEmpty()) {
            return 0;
        }
        List<Double> ordenadas = new ArrayList<>(muestras);
        Collections.sort(ordenadas);
        int indice = (int) Math.ceil((p / 100.0) * ordenadas.size()) - 1;
        return ordenadas.get(Math.max(0, indice));
    }

    public double p50() {
        return percentil(50);
    }

    public double p95() {
        return percentil(95);
    }

    public double p99() {
        return percentil(99);
    }

    public double maximo() {
        return percentil(100);
    }
}
