package co.edu.icesi.apexstore.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistogramaTest {
    @Test
    void calculaPercentilesOrdenados() {
        Histograma histograma = new Histograma(100);
        for (int i = 1; i <= 100; i++) {
            histograma.registrar(i);
        }
        assertEquals(50, histograma.p50());
        assertEquals(95, histograma.p95());
        assertEquals(99, histograma.p99());
        assertEquals(100, histograma.maximo());
    }

    @Test
    void rechazaValoresInvalidos() {
        Histograma histograma = new Histograma(2);
        assertThrows(IllegalArgumentException.class, () -> histograma.registrar(-1));
        assertThrows(IllegalArgumentException.class, () -> histograma.percentil(101));
    }
}
