package com.correoargentino.sga.utils;

import org.apache.coyote.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Function;

/** Conversión y validación de campos de un body JSON deserializado como Map. */
public final class Campos {

    private Campos() {}

    // =====================================================
    // Conversores (no lanzan BadRequestException)
    // =====================================================

    public static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** true si es null o solo contiene espacios. */
    public static boolean isBlank(Object o) {
        String s = str(o);
        return s == null || s.isBlank();
    }

    public static String blankToNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    public static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Integer.parseInt(s);
    }

    /** Formato argentino: el punto es separador de miles y la coma es decimal ("1.234,56"). */
    public static BigDecimal asDecimal(Object o) {
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

    /** Formato plano: acepta punto o coma como separador decimal ("1234.56" / "1234,56"). */
    public static BigDecimal plainDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());

        String s = o.toString().trim().replace(",", ".");
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** null → hoy. Toma los primeros 10 caracteres (yyyy-MM-dd). */
    public static LocalDate asDateOrToday(Object o) {
        if (o == null) return LocalDate.now();
        if (o instanceof LocalDate d) return d;
        String s = o.toString().trim();
        return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
    }

    // =====================================================
    // Validaciones de campos obligatorios
    // =====================================================

    public static String requireString(Map<?, ?> body, String key, String nombre)
            throws BadRequestException {

        Object value = requirePresente(body, key, nombre);

        if (!(value instanceof String s)) {
            throw error(nombre, "debe ser texto");
        }
        if (s.isBlank()) {
            throw error(nombre, "no puede estar vacío");
        }
        return s;
    }

    public static Integer requireInt(Map<?, ?> body, String key, String nombre)
            throws BadRequestException {
        return requireNumero(body, key, nombre, Campos::asInt, "debe ser un número entero válido");
    }

    public static BigDecimal requireDecimal(Map<?, ?> body, String key, String nombre)
            throws BadRequestException {
        return requireNumero(body, key, nombre, Campos::asDecimal, "debe ser un número válido");
    }

    public static Long requireLongPositivo(Map<?, ?> body, String key, String nombre)
            throws BadRequestException {

        Long value = requireNumero(body, key, nombre, Campos::asLong, "debe ser un número válido");

        if (value <= 0) {
            throw error(nombre, "debe ser mayor a 0");
        }
        return value;
    }

    /** Fecha obligatoria como texto yyyy-MM-dd. */
    public static LocalDate requireDate(Map<?, ?> body, String key, String nombre)
            throws BadRequestException {

        Object value = requirePresente(body, key, nombre);

        if (!(value instanceof String s)) {
            throw error(nombre, "debe tener formato yyyy-MM-dd");
        }

        String text = s.trim();
        if (text.isEmpty()) {
            throw error(nombre, "no puede estar vacío");
        }

        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw error(nombre, "debe tener una fecha válida con formato yyyy-MM-dd");
        }
    }

    // =====================================================
    // Internos
    // =====================================================

    private static Object requirePresente(Map<?, ?> body, String key, String nombre)
            throws BadRequestException {

        if (!body.containsKey(key)) {
            throw error(nombre, "es obligatorio");
        }

        Object value = body.get(key);
        if (value == null) {
            throw error(nombre, "no puede ser null");
        }
        return value;
    }

    private static <T> T requireNumero(Map<?, ?> body,
                                       String key,
                                       String nombre,
                                       Function<Object, T> parser,
                                       String mensajeInvalido) throws BadRequestException {

        Object value = requirePresente(body, key, nombre);

        T parsed;
        try {
            parsed = parser.apply(value);
        } catch (RuntimeException e) {
            throw error(nombre, mensajeInvalido);
        }

        if (parsed == null) {
            throw error(nombre, "no puede estar vacío");
        }
        return parsed;
    }

    private static BadRequestException error(String nombre, String detalle) {
        return new BadRequestException("El campo '" + nombre + "' " + detalle + ".");
    }
}