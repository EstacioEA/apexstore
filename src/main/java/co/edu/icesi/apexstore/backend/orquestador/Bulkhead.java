package co.edu.icesi.apexstore.backend.orquestador;

import java.util.concurrent.Semaphore;

/** Límite de concurrencia aislado por estrategia. */
public final class Bulkhead {
    private final Semaphore permisos;

    public Bulkhead(int maxConcurrentes) {
        if (maxConcurrentes <= 0) {
            throw new IllegalArgumentException("El bulkhead debe ser positivo");
        }
        permisos = new Semaphore(maxConcurrentes);
    }

    public boolean tryAcquire() {
        return permisos.tryAcquire();
    }

    public void release() {
        permisos.release();
    }

    public int disponibles() {
        return permisos.availablePermits();
    }
}
