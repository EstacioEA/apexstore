package co.edu.icesi.apexstore.comun;

/** Reloj basado en el reloj del sistema. */
public final class RelojSistema implements Reloj {
    @Override
    public long ahoraMs() {
        return System.currentTimeMillis();
    }
}
