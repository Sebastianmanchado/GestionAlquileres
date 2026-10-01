package com.correoargentino.sga.utils;


import java.math.BigDecimal;
import java.time.LocalDate;

public record FacturaValidada(
    String cuit,
    String nis,
    String razonSocial,
    String comprobante,
    String tipoFactura,
    String observaciones,
    BigDecimal total,
    BigDecimal neto,
    BigDecimal iva,
    LocalDate periodo,
    LocalDate fechaEmision,
    Integer puntoVenta,
    Integer tipoComprobanteId,
    String cae,
    LocalDate fechaVtoCae,
    String moneda
) {}