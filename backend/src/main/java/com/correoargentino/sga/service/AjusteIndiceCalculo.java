package com.correoargentino.sga.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.NavigableMap;

/**
 * Cálculo puro del ajuste por índice. IPC compone las variaciones mensuales
 * (en porcentaje) de todos los meses del período. ICL usa el nivel del día,
 * o el último publicado hasta {@code diasAtras}.
 */
public final class AjusteIndiceCalculo {

    public static final int DIAS_TOLERANCIA_ICL = 7;

    private AjusteIndiceCalculo() {
    }

    public static int meses(String periodicidad) {
        if (periodicidad == null) return 0;
        return switch (periodicidad.trim().toUpperCase()) {
            case "TRIMESTRAL" -> 3;
            case "CUATRIMESTRAL" -> 4;
            case "SEMESTRAL" -> 6;
            default -> 0;
        };
    }

    /**
     * Factor IPC del período. Toma las variaciones mensuales en porcentaje de los
     * {@code meses} corridos desde el mes de {@code base}. Un trimestral usa los
     * tres meses; si falta alguno, no hay coeficiente.
     */
    public static BigDecimal coeficientePorVariacionMensual(
            NavigableMap<LocalDate, BigDecimal> variaciones,
            LocalDate base,
            int meses) {
        if (variaciones == null || base == null || meses <= 0) return null;
        BigDecimal factor = BigDecimal.ONE;
        LocalDate mes = base.withDayOfMonth(1);
        for (int i = 0; i < meses; i++) {
            BigDecimal variacion = variaciones.get(mes);
            if (variacion == null) return null;
            factor = factor.multiply(BigDecimal.ONE.add(variacion.movePointLeft(2)));
            mes = mes.plusMonths(1);
        }
        return factor.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal coeficiente(BigDecimal nivelNuevo, BigDecimal nivelBase) {
        if (nivelNuevo == null || nivelBase == null) return null;
        if (nivelNuevo.signum() <= 0 || nivelBase.signum() <= 0) return null;
        return nivelNuevo.divide(nivelBase, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal importeAjustado(BigDecimal importe, BigDecimal coeficiente) {
        return importe.multiply(coeficiente).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nivelEnFechaOAnterior(NavigableMap<LocalDate, BigDecimal> niveles, LocalDate fecha, int diasAtras) {
        if (niveles == null || niveles.isEmpty() || fecha == null) return null;
        var entry = niveles.floorEntry(fecha);
        if (entry == null) return null;
        if (entry.getKey().isBefore(fecha.minusDays(diasAtras))) return null;
        return entry.getValue();
    }

    public static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        String text = value.toString().trim();
        if (text.length() < 10) return null;
        return LocalDate.parse(text.substring(0, 10));
    }
}
