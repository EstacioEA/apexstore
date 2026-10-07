package co.edu.icesi.apexstore.db;

import co.edu.icesi.apexstore.arranque.NodoDb;

/** Punto de entrada del proceso de base de datos. */
public final class DbServer {
    private DbServer() {
    }

    public static void main(String[] args) {
        NodoDb.ejecutar(args);
    }
}
