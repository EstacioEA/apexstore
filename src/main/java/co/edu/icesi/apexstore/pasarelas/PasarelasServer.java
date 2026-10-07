package co.edu.icesi.apexstore.pasarelas;

import co.edu.icesi.apexstore.arranque.NodoPasarelas;

/** Punto de entrada del proceso de pasarelas. */
public final class PasarelasServer {
    private PasarelasServer() {
    }

    public static void main(String[] args) {
        NodoPasarelas.ejecutar(args);
    }
}
