package co.edu.icesi.apexstore.comun;

/** Formatea identificadores de correlación para los logs de negocio. */
public final class Correlacion {
    private Correlacion() {
    }

    public static String etiqueta(String componente, String ordenId, String transaccionId) {
        return "[" + componente + "][ord=" + seguro(ordenId) + "|tx=" + seguro(transaccionId) + "]";
    }

    private static String seguro(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }
}
