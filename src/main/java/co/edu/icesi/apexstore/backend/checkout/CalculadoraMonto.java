package co.edu.icesi.apexstore.backend.checkout;

import ApexStore.Comun.LineaCompra;

/** Calcula el importe minorista sin usar punto flotante. */
public final class CalculadoraMonto {
    private CalculadoraMonto() {
    }

    public static long calcular(LineaCompra[] lineas) {
        if (lineas == null || lineas.length == 0) {
            throw new IllegalArgumentException("Debe existir al menos una línea");
        }
        long total = 0;
        for (LineaCompra linea : lineas) {
            if (linea == null || linea.cantidad <= 0 || linea.precioUnitarioMinor <= 0) {
                throw new IllegalArgumentException("Línea de compra inválida");
            }
            total = Math.addExact(total, Math.multiplyExact(linea.cantidad, linea.precioUnitarioMinor));
        }
        return total;
    }
}
