package co.edu.icesi.apexstore.pasarelas;

import ApexStore.Pruebas.ConfigPasarela;
import java.util.concurrent.atomic.AtomicReference;

/** Configuración mutable y thread-safe de una pasarela simulada. */
public final class ConfigSimulacion {
    private final AtomicReference<ConfigPasarela> actual;

    public ConfigSimulacion(ConfigPasarela inicial) {
        actual = new AtomicReference<>(copiar(inicial));
    }

    public ConfigPasarela obtener() {
        return copiar(actual.get());
    }

    public void configurar(ConfigPasarela nueva) {
        if (nueva == null || nueva.tasaRechazo < 0 || nueva.tasaRechazo > 1
                || nueva.latenciaAckMinMs < 0 || nueva.latenciaAckMaxMs < nueva.latenciaAckMinMs
                || nueva.latenciaCallbackMinMs < 0
                || nueva.latenciaCallbackMaxMs < nueva.latenciaCallbackMinMs) {
            throw new IllegalArgumentException("Configuración de pasarela inválida");
        }
        actual.set(copiar(nueva));
    }

    private static ConfigPasarela copiar(ConfigPasarela c) {
        return new ConfigPasarela(c.modo, c.latenciaAckMinMs, c.latenciaAckMaxMs,
                c.latenciaCallbackMinMs, c.latenciaCallbackMaxMs, c.tasaRechazo);
    }
}
