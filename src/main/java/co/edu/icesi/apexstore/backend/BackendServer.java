package co.edu.icesi.apexstore.backend;

import co.edu.icesi.apexstore.arranque.NodoBackend;

/** Punto de entrada del proceso backend. */
public final class BackendServer {
    private BackendServer() {
    }

    public static void main(String[] args) {
        NodoBackend.ejecutar(args);
    }
}
