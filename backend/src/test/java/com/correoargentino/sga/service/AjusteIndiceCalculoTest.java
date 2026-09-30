package com.correoargentino.sga.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AjusteIndiceCalculoTest {

    @Test
    void periodicidadEnMeses() {
        assertEquals(3, AjusteIndiceCalculo.meses("TRIMESTRAL"));
        assertEquals(4, AjusteIndiceCalculo.meses("CUATRIMESTRAL"));
        assertEquals(6, AjusteIndiceCalculo.meses("SEMESTRAL"));
        assertEquals(0, AjusteIndiceCalculo.meses("MENSUAL"));
        assertEquals(0, AjusteIndiceCalculo.meses(null));
    }

    @Test
    void ipcTomaElMesAnterior() {
        assertEquals(LocalDate.of(2026, 3, 1), AjusteIndiceCalculo.mesIndiceIpc(LocalDate.of(2026, 4, 1)));
        assertEquals(LocalDate.of(2025, 12, 1), AjusteIndiceCalculo.mesIndiceIpc(LocalDate.of(2026, 1, 15)));
    }

    @Test
    void coeficienteEImporte() {
        BigDecimal coef = AjusteIndiceCalculo.coeficiente(new BigDecimal("110.50"), new BigDecimal("100"));
        assertEquals(new BigDecimal("1.1050"), coef);
        assertEquals(new BigDecimal("110500.00"), AjusteIndiceCalculo.importeAjustado(new BigDecimal("100000"), coef));
        assertNull(AjusteIndiceCalculo.coeficiente(null, BigDecimal.ONE));
        assertNull(AjusteIndiceCalculo.coeficiente(BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void iclUsaElUltimoValorDentroDeLaVentana() {
        TreeMap<LocalDate, BigDecimal> niveles = new TreeMap<>();
        niveles.put(LocalDate.of(2026, 4, 1), new BigDecimal("10"));
        niveles.put(LocalDate.of(2026, 4, 8), new BigDecimal("11"));
        niveles.put(LocalDate.of(2026, 4, 20), new BigDecimal("12"));

        assertEquals(new BigDecimal("11"),
                AjusteIndiceCalculo.nivelEnFechaOAnterior(niveles, LocalDate.of(2026, 4, 10), 7));
        assertNull(AjusteIndiceCalculo.nivelEnFechaOAnterior(niveles, LocalDate.of(2026, 4, 19), 7));
        assertEquals(new BigDecimal("12"),
                AjusteIndiceCalculo.nivelEnFechaOAnterior(niveles, LocalDate.of(2026, 4, 20), 7));
    }
}
