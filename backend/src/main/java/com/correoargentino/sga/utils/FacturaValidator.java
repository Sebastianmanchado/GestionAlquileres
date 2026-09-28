package com.correoargentino.sga.utils;


import org.apache.coyote.BadRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import com.correoargentino.sga.repo.SgaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
public
class FacturaValidator {

    private static final BigDecimal IVA_FACTOR = new BigDecimal("1.21");
    private static final int CUIT_LARGO_MINIMO = 11;
    private static final int MONEDA_LARGO = 3;
    private static final int TIPO_COMPROBANTE_POR_DEFECTO = 1;

    private final SgaRepository repo;

    public FacturaValidator(SgaRepository repo) {
        this.repo = repo;
    }

    public FacturaValidada validar(Map<String, Object> body) throws BadRequestException {

        String cuit = requireString(body, "cuit", "CUIT")
            .replace("-", "")
            .trim();

        if (cuit.length() < CUIT_LARGO_MINIMO) {
            throw new BadRequestException("El CUIT debe tener al menos 11 caracteres.");
        }

        String razonSocial = requireString(body, "razonSocial", "Razón Social");
        String comprobante = requireString(body, "comprobante", "Comprobante");

        BigDecimal total = importe(body.get("importe"));
        YearMonth periodo = periodo(body.get("periodo"));
        LocalDate fechaEmision = fechaEmision(body.get("fechaEmision"));

        BigDecimal neto = total.divide(IVA_FACTOR, 2, RoundingMode.HALF_UP);

        return new FacturaValidada(
            cuit,
            (String) body.get("nis"),
            razonSocial,
            comprobante,
            (String) body.get("observaciones"),
            total,
            neto,
            total.subtract(neto),
            periodo.atDay(1),
            fechaEmision,
            puntoVenta(body.get("puntoVenta")),
            tipoComprobanteId(texto(body.get("tipo"))),
            textoOpcional(body.get("cae")),
            fechaOpcional(body.get("fechaVencimientoCae"), "fecha de vencimiento del CAE"),
            moneda(body.get("moneda"))
        );
    }

    // ---------- validaciones de campos obligatorios ----------

    private static BigDecimal importe(Object raw) throws BadRequestException {
        BigDecimal total = asDecimal(raw);

        if (total == null) {
            throw new BadRequestException("El importe debe ser un número válido.");
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El importe debe ser mayor a 0.");
        }
        return total;
    }

    private static YearMonth periodo(Object raw) throws BadRequestException {
        YearMonth periodo = asYearMonth(raw);

        if (periodo == null) {
            throw new BadRequestException("El período no es válido.");
        }
        if (periodo.isAfter(YearMonth.from(LocalDate.now()))) {
            throw new BadRequestException("El período no puede ser mayor al mes actual.");
        }
        return periodo;
    }

    private static LocalDate fechaEmision(Object raw) throws BadRequestException {
        LocalDate fecha = asDate(raw);

        if (fecha == null) {
            throw new BadRequestException("La fecha de emisión no es válida.");
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw new BadRequestException(
                "La fecha de emisión no puede ser mayor a la fecha actual.");
        }
        return fecha;
    }

    // ---------- campos opcionales ----------

    private Integer tipoComprobanteId(String tipo) throws BadRequestException {
        if (tipo == null || tipo.isBlank()) {
            return TIPO_COMPROBANTE_POR_DEFECTO;
        }

        String codigo = switch (tipo.trim().toUpperCase()) {
            case "A", "FA" -> "FA";
            case "B", "FB" -> "FB";
            case "C", "FC" -> "FC";
            default -> throw new BadRequestException(
                "El tipo de comprobante '" + tipo + "' no es válido. Use A o C.");
        };

        List<Integer> ids = repo.jdbc().query(
            InvoiceSql.TIPO_COMPROBANTE_POR_CODIGO,
            new MapSqlParameterSource("codigo", codigo),
            (rs, rowNum) -> rs.getInt("id")
        );

        if (ids.isEmpty()) {
            throw new BadRequestException("No existe el tipo de comprobante " + codigo + ".");
        }
        return ids.get(0);
    }

    private static Integer puntoVenta(Object raw) throws BadRequestException {
        String s = textoOpcional(raw);
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BadRequestException("El punto de venta no es válido.");
        }
    }

    private static LocalDate fechaOpcional(Object raw, String nombre) throws BadRequestException {
        if (textoOpcional(raw) == null) {
            return null;
        }
        LocalDate fecha = asDate(raw);
        if (fecha == null) {
            throw new BadRequestException("La " + nombre + " no es válida.");
        }
        return fecha;
    }

    private static String moneda(Object raw) throws BadRequestException {
        String s = textoOpcional(raw);
        if (s == null) {
            return null;
        }
        s = s.toUpperCase();
        if (s.length() != MONEDA_LARGO) {
            throw new BadRequestException("La moneda debe tener 3 caracteres.");
        }
        return s;
    }

    // ---------- conversores ----------

    private static String requireString(Map<String, Object> body, String key, String nombreCampo)
            throws BadRequestException {

        if (!(body.get(key) instanceof String value) || value.isBlank()) {
            throw new BadRequestException(
                "El campo '" + nombreCampo + "' es obligatorio y debe tener contenido.");
        }
        return value.trim();
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }

    private static String textoOpcional(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal asDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());

        String s = o.toString()
            .replaceAll("[^0-9,.-]", "")
            .replace(".", "")
            .replace(",", ".");

        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate asDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;

        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        if (s.matches("\\d{4}-\\d{2}")) return LocalDate.parse(s + "-01");
        if (s.length() >= 10) return LocalDate.parse(s.substring(0, 10));
        return null;
    }

    private static YearMonth asYearMonth(Object value) {
        if (value == null) return null;
        try {
            return YearMonth.parse(value.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}