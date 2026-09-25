package com.correoargentino.sga.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Arma el mensaje de asiento que viaja a SAP, con dos posiciones
 * (clave 31 proveedor y clave 40 gasto) y la cabecera repetida.
 */
public final class AsientoContableXml {

    public static final String SOCIEDAD = "COAR";
    public static final String REF1 = "ALQU";
    public static final String DIVISION_PROVEEDOR = "2000";
    public static final String CAL_IMP_GASTO = "X";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private AsientoContableXml() {
    }

    public record Datos(
            int numero,
            LocalDate fechaDoc,
            String claseDoc,
            LocalDate fechaCont,
            String moneda,
            String referencia,
            String texto,
            String acreedor,
            String cuentaGasto,
            String divisionGasto,
            String ceco,
            String indicadorImpuesto,
            BigDecimal importe,
            String cae,
            LocalDate fechaVtoCae
    ) {
    }

    public static String referencia(int puntoVenta, String letra, String numero) {
        String digitos = numero == null ? "" : numero.replaceAll("\\D", "");
        long nro = digitos.isEmpty() ? 0 : Long.parseLong(digitos);
        return String.format("%05d", puntoVenta) + letra + String.format("%08d", nro);
    }

    public static String texto(LocalDate periodo, String destinoCodigo) {
        return "ALQ " + periodo.getMonthValue() + "/" + periodo.getYear() + " " + destinoCodigo;
    }

    public static String build(Datos datos) {
        StringBuilder xml = new StringBuilder();
        xml.append("<n0:MT_AsientosContables_Proxy_R3_Req xmlns:n0=\"urn:sat/asientos_contables\">");
        xml.append(posicion(datos, true));
        xml.append(posicion(datos, false));
        xml.append("</n0:MT_AsientosContables_Proxy_R3_Req>");
        return xml.toString();
    }

    private static String posicion(Datos datos, boolean proveedor) {
        String importe = datos.importe().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String numero = Integer.toString(datos.numero());
        StringBuilder xml = new StringBuilder();
        xml.append("<AsientoContable>");
        tag(xml, "ASIENTO", numero);
        tag(xml, "FECHA_DOC", fecha(datos.fechaDoc()));
        tag(xml, "CLASE_DOC", datos.claseDoc());
        tag(xml, "SOCIEDAD", SOCIEDAD);
        tag(xml, "FECHA_CONT", fecha(datos.fechaCont()));
        tag(xml, "MONEDA", datos.moneda());
        tag(xml, "REFERENCIA", datos.referencia());
        tag(xml, "TEXTO_CAB", datos.texto());
        tag(xml, "TIENDA", "");
        tag(xml, "CLAVE_CONT", proveedor ? "31" : "40");
        tag(xml, "CUENTA", proveedor ? datos.acreedor() : datos.cuentaGasto());
        tag(xml, "INCME", "");
        tag(xml, "CL_MOV", "");
        tag(xml, "IMPORTE", importe);
        tag(xml, "CAL_IMP", proveedor ? "" : CAL_IMP_GASTO);
        tag(xml, "IND_IMP", proveedor ? "" : datos.indicadorImpuesto());
        tag(xml, "DIVISION", proveedor ? DIVISION_PROVEEDOR : datos.divisionGasto());
        tag(xml, "FECHA", proveedor ? fecha(datos.fechaCont()) : "");
        tag(xml, "CECO", proveedor ? "" : datos.ceco());
        tag(xml, "ORDEN", "");
        tag(xml, "ASIGNACION", proveedor ? datos.acreedor() : "");
        tag(xml, "TEXTO_POSICION", datos.texto());
        tag(xml, "REF1", REF1);
        tag(xml, "REF2", numero);
        tag(xml, "REF3", "");
        tag(xml, "CEBE", "");
        tag(xml, "NOTA", "");
        tag(xml, "CAE", proveedor ? datos.cae() : "");
        tag(xml, "FECHA_VTO", proveedor ? fecha(datos.fechaVtoCae()) : "");
        xml.append("</AsientoContable>");
        return xml.toString();
    }

    private static void tag(StringBuilder xml, String name, String value) {
        xml.append('<').append(name).append('>')
                .append(escape(value))
                .append("</").append(name).append('>');
    }

    private static String fecha(LocalDate date) {
        return date == null ? "" : FECHA.format(date);
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
