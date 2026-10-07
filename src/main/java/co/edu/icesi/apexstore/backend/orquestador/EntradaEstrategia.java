package co.edu.icesi.apexstore.backend.orquestador;

import ApexStore.Pagos.EstrategiaPagosPrx;

/** Dependencias y protecciones de una estrategia registrada. */
public record EntradaEstrategia(EstrategiaPagosPrx proxy, CircuitBreaker breaker, Bulkhead bulkhead) {
}
