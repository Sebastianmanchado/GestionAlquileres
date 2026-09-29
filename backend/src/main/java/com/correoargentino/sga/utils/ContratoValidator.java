package com.correoargentino.sga.utils;


import org.apache.coyote.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.correoargentino.sga.utils.Campos.*;

/** Validaciones del body de contrato. No accede a la base. */
public final class ContratoValidator {

    private static final int CUIT_DIGITOS = 11;
    private static final int PORCENTAJE_TOTAL = 100;

    private ContratoValidator() {}

    // =====================================================
    // Validación común de alta y edición
    // =====================================================

    /**
     * Valida el body y normaliza algunos campos en el mismo Map:
     * localidadId, regionId, indiceId (→ Integer) y cuit (→ Long).
     */
    public static void validar(Map<String, Object> body) throws BadRequestException {

        requireInt(body, "tipoContratoId", "Tipo de Contrato");

        boolean mensual = esMensual(body);
        LocalDate inicio = null;
        LocalDate vencimiento = null;

        if (mensual) {
            inicio = requireDate(body, "fechaInicio", "Fecha de Inicio");
            vencimiento = requireDate(body, "fechaVencimiento", "Fecha de Vencimiento");
        }

        body.put("localidadId", requireInt(body, "localidadId", "Localidad"));
        body.put("regionId", requireInt(body, "regionId", "Región"));

        if ("existente".equals(str(body.get("modoIndice")))) {
            body.put("indiceId", requireInt(body, "indiceId", "Indice de Ajuste"));
        }

        requireString(body, "nis", "NIS");
        requireString(body, "denominacion", "Unidad de Negocio");
        requireString(body, "direccion", "Dirección");
        requireDecimal(body, "importeTotal", "Importe Total");
        requireString(body, "periodicidad", "Frecuencia");
        requireDecimal(body, "tolerancia", "Tolerancia de Diferencia");

        if (body.containsKey("inmuebleId")) {
            requireLongPositivo(body, "inmuebleId", "Inmueble");
        }
        if (body.containsKey("razonSocial")) {
            requireString(body, "razonSocial", "Razon Social");
        }
        if (body.containsKey("cuit")) {
            body.put("cuit", Long.valueOf(normalizarCuit(body.get("cuit"))));
        }
        if (body.containsKey("locadorId") && body.get("locadorId") != "") {
            requireLongPositivo(body, "locadorId", "Locador");
        }

        if (mensual && vencimiento.isBefore(inicio)) {
            throw new BadRequestException(
                "La fecha de vencimiento no puede ser anterior a la fecha de inicio.");
        }

        validarFacturasPlanificadas(body.get("facturas_planificadas"));
    }

    /** Quita guiones y espacios; exige exactamente 11 dígitos. */
    public static String normalizarCuit(Object value) throws BadRequestException {

        if (value == null) {
            throw new BadRequestException("El campo CUIT no puede ser null.");
        }

        String cuit = String.valueOf(value).trim();
        if (cuit.isEmpty()) {
            throw new BadRequestException("El campo CUIT no puede estar vacío.");
        }

        cuit = cuit.replace("-", "").replace(" ", "");

        if (!cuit.matches("\\d+")) {
            throw new BadRequestException("El campo CUIT debe contener solamente números.");
        }
        if (cuit.length() != CUIT_DIGITOS) {
            throw new BadRequestException("El campo CUIT debe tener exactamente 11 dígitos.");
        }
        return cuit;
    }

    // =====================================================
    // Validaciones exclusivas del alta
    // =====================================================

    public static int cantidadFacturas(Map<String, Object> body) throws BadRequestException {
        int cantidad = asInt(body.getOrDefault("cantidad_facturas", 1));

        if (cantidad < 1) {
            throw new BadRequestException("La cantidad de facturas debe ser mayor o igual a 1.");
        }
        return cantidad;
    }

    /** Si no viene vencimiento, se calcula como inicio + cantidad de facturas (en meses). */
    public static LocalDate vencimiento(Map<String, Object> body,
                                        LocalDate inicio,
                                        int cantidadFacturas) throws BadRequestException {

        Object raw = body.get("fechaVencimiento");
        LocalDate vencimiento = isBlank(raw)
            ? inicio.plusMonths(cantidadFacturas)
            : asDateOrToday(raw);

        if (!vencimiento.isAfter(inicio)) {
            throw new BadRequestException(
                "La fecha de vencimiento debe ser posterior a la fecha de inicio.");
        }
        return vencimiento;
    }

    public static BigDecimal deposito(Map<String, Object> body) throws BadRequestException {
        BigDecimal deposito = asDecimal(body.get("deposito"));

        if (deposito != null && deposito.signum() < 0) {
            throw new BadRequestException("El deposito debe ser mayor o igual a 0.");
        }
        return deposito;
    }

    // =====================================================
    // Internos
    // =====================================================

    /**
     * ⚠ Comparación por REFERENCIA, preservada del código original.
     * Con un body deserializado desde JSON nunca es true, así que las fechas
     * no se validan acá. Para corregirlo: "mensual".equals(body.get("tipoFacturacion")).
     */
    @SuppressWarnings("StringEquality")
    private static boolean esMensual(Map<String, Object> body) {
        return body.get("tipoFacturacion") == "mensual";
    }

    private static void validarFacturasPlanificadas(Object raw) throws BadRequestException {

        if (raw == null) {
            throw new BadRequestException("Las facturas son obligatorias.");
        }
        if (!(raw instanceof List<?> facturas)) {
            throw new BadRequestException("Las facturas deben ser una lista.");
        }
        if (facturas.isEmpty()) {
            throw new BadRequestException("Debe existir al menos una factura.");
        }

        int totalPorcentaje = 0;

        for (int i = 0; i < facturas.size(); i++) {

            if (!(facturas.get(i) instanceof Map<?, ?> factura)) {
                throw new BadRequestException(
                    "La factura en la posición " + i + " no tiene un formato válido.");
            }

            int porcentaje = requireInt(factura, "porcentaje", "Porcentaje de facturas");
            BigDecimal importe = requireDecimal(factura, "importe", "Importe");

            if (porcentaje < 0 || porcentaje > PORCENTAJE_TOTAL) {
                throw new BadRequestException(
                    "El porcentaje de facturas[" + i + "].porcentaje debe estar entre 0 y 100.");
            }
            if (importe.signum() < 0) {
                throw new BadRequestException(
                    "El importe de facturas[" + i + "].importe debe ser mayor o igual a 0.");
            }

            totalPorcentaje += porcentaje;
        }

        if (totalPorcentaje != PORCENTAJE_TOTAL) {
            throw new BadRequestException(
                "La suma de los porcentajes de las facturas debe ser 100%.");
        }
    }
}