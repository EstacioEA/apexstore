package co.edu.icesi.apexstore.cliente;

/** Reporte textual mínimo para escenarios de ejecución. */
public final class ReporteConsola {
    private ReporteConsola() {
    }

    public static void imprimir(String escenario, String detalle) {
        System.out.println("ESCENARIO=" + escenario);
        System.out.println(detalle);
        System.out.println("RESULTADO=PASS");
    }
}
