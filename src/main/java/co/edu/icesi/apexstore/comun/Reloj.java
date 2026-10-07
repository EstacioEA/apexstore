package co.edu.icesi.apexstore.comun;

/** Abstracción del tiempo para permitir pruebas deterministas. */
public interface Reloj {
    long ahoraMs();
}
