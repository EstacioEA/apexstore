package co.edu.icesi.apexstore.arranque;

import java.util.concurrent.CountDownLatch;

/** Levanta los nodos de infraestructura en una JVM con communicators separados. */
public final class RunAll {
    private RunAll() {
    }

    public static void main(String[] args) throws InterruptedException {
        Thread db = new Thread(() -> NodoDb.ejecutar(new String[]{"--config=config/db.properties"}), "nodo-db");
        Thread pasarelas = new Thread(
                () -> NodoPasarelas.ejecutar(new String[]{"--config=config/pasarelas.properties"}),
                "nodo-pasarelas");
        Thread backend = new Thread(
                () -> NodoBackend.ejecutar(new String[]{"--config=config/backend.properties"}),
                "nodo-backend");
        db.start();
        Thread.sleep(800);
        pasarelas.start();
        Thread.sleep(800);
        backend.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            db.interrupt();
            pasarelas.interrupt();
            backend.interrupt();
        }, "apagado-runall"));
        new CountDownLatch(1).await();
    }
}
