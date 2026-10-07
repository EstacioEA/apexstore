package co.edu.icesi.apexstore.comun;

import java.util.UUID;

/** Genera identificadores de dominio únicos. */
public final class Ids {
    private Ids() {
    }

    public static String nuevoOrden() {
        return "ord_" + UUID.randomUUID();
    }

    public static String nuevaTransaccion() {
        return "tx_" + UUID.randomUUID();
    }
}
