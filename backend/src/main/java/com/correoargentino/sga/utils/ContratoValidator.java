package com.correoargentino.sga.utils;


import org.apache.coyote.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        locadores(body);
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

    /** Locador tal como llega en el body, ya validado y normalizado. */
    public record LocadorBody(
        Long locadorId,
        String razonSocial,
        String cuit,
        String email,
        String telefono,
        String cbu,
        int porcentaje,
        String acreedorSapCodigo,
        String cecoSap,
        String divisionSap,
        String cuentaGasto,
        String indicadorImpuesto) {}

    /**
     * Valida body.locadores: al menos uno, porcentajes entre 0 y 100 que sumen 100,
     * cada uno existente (locadorId) o nuevo (razonSocial + CUIT) y sin repetidos.
     */
    public static List<LocadorBody> locadores(Map<String, Object> body) throws BadRequestException {

        Object raw = body.get("locadores");

        if (!(raw instanceof List<?> lista) || lista.isEmpty()) {
            throw new BadRequestException("Debe indicar al menos un locador.");
        }

        List<LocadorBody> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<String> cuits = new HashSet<>();
        int total = 0;

        for (int i = 0; i < lista.size(); i++) {

            if (!(lista.get(i) instanceof Map<?, ?> l)) {
                throw new BadRequestException(
                    "El locador en la posición " + i + " no tiene un formato válido.");
            }

            String etiqueta = "Locador " + (i + 1);

            int porcentaje = requireInt(l, "porcentaje", "Porcentaje de " + etiqueta);
            if (porcentaje < 0 || porcentaje > PORCENTAJE_TOTAL) {
                throw new BadRequestException(
                    "El porcentaje del " + etiqueta + " debe estar entre 0 y 100.");
            }
            total += porcentaje;

            Long locadorId = isBlank(l.get("locadorId"))
                ? null
                : requireLongPositivo(l, "locadorId", etiqueta);

            String razonSocial = null;
            String cuit = null;

            if (locadorId == null) {
                razonSocial = requireString(l, "razonSocial", "Razón social del " + etiqueta).trim();
                cuit = normalizarCuit(l.get("cuit"));

                if (!cuits.add(cuit)) {
                    throw new BadRequestException("El CUIT " + cuit + " está repetido en el contrato.");
                }
            } else if (!ids.add(locadorId)) {
                throw new BadRequestException("El " + etiqueta + " está repetido en el contrato.");
            }

            String cbu = blankToNull(l.get("cbu"));
            if (cbu != null && !cbu.matches("\\d{22}")) {
                throw new BadRequestException("El CBU del " + etiqueta + " debe tener 22 dígitos.");
            }

            result.add(new LocadorBody(
                locadorId,
                razonSocial,
                cuit,
                blankToNull(l.get("email")),
                blankToNull(l.get("telefono")),
                cbu,
                porcentaje,
                blankToNull(l.get("acreedorSapCodigo")),
                blankToNull(l.get("cecoSap")),
                blankToNull(l.get("divisionSap")),
                blankToNull(l.get("cuentaGasto")),
                blankToNull(l.get("indicadorImpuesto"))));
        }

        if (total != PORCENTAJE_TOTAL) {
            throw new BadRequestException("La suma de los porcentajes de los locadores debe ser 100%.");
        }

        return result;
    }
}