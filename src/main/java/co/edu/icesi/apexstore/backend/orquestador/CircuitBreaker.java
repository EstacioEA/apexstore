package co.edu.icesi.apexstore.backend.orquestador;

import co.edu.icesi.apexstore.comun.Reloj;
import co.edu.icesi.apexstore.comun.RelojSistema;

/** Circuit breaker por medio de pago. */
public final class CircuitBreaker {
    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private final int umbralFallos;
    private final long abiertoMs;
    private final Reloj reloj;
    private Estado estado = Estado.CERRADO;
    private int fallos;
    private long abiertoDesde;
    private boolean pruebaEnCurso;

    public CircuitBreaker(int umbralFallos, long abiertoMs) {
        this(umbralFallos, abiertoMs, new RelojSistema());
    }

    public CircuitBreaker(int umbralFallos, long abiertoMs, Reloj reloj) {
        if (umbralFallos <= 0 || abiertoMs < 0) {
            throw new IllegalArgumentException("Configuración de breaker inválida");
        }
        this.umbralFallos = umbralFallos;
        this.abiertoMs = abiertoMs;
        this.reloj = reloj;
    }

    public synchronized boolean permite() {
        if (estado == Estado.CERRADO) {
            return true;
        }
        if (estado == Estado.ABIERTO && reloj.ahoraMs() - abiertoDesde >= abiertoMs) {
            estado = Estado.SEMIABIERTO;
            pruebaEnCurso = false;
        }
        if (estado == Estado.SEMIABIERTO && !pruebaEnCurso) {
            pruebaEnCurso = true;
            return true;
        }
        return false;
    }

    public synchronized void exito() {
        estado = Estado.CERRADO;
        fallos = 0;
        pruebaEnCurso = false;
    }

    public synchronized void fallo() {
        if (estado == Estado.SEMIABIERTO || ++fallos >= umbralFallos) {
            estado = Estado.ABIERTO;
            abiertoDesde = reloj.ahoraMs();
            pruebaEnCurso = false;
        }
    }

    public synchronized Estado estado() {
        permite();
        return estado;
    }
}
