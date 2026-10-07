package co.edu.icesi.apexstore.comun;

/** Utilidades para analizar configuraciones simples. */
public final class ConfigUtil {
    private ConfigUtil() {
    }

    public static long[] rango(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Rango vacío");
        }
        String[] partes = valor.trim().split("-", -1);
        try {
            long minimo = Long.parseLong(partes[0].trim());
            long maximo = partes.length == 1 ? minimo : Long.parseLong(partes[1].trim());
            if (minimo < 0 || maximo < minimo) {
                throw new IllegalArgumentException("Rango inválido: " + valor);
            }
            return new long[]{minimo, maximo};
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Rango inválido: " + valor, e);
        }
    }
}
