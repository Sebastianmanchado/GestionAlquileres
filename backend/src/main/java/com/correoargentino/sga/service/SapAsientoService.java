package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.SapNoConfiguradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SapAsientoService {

    private static final Set<String> ENVIABLES = Set.of("OK", "OK_CON_DIF");

    private final SgaRepository repo;
    private final SapAsientoClient client;
    private final CurrentUserProvider currentUser;
    private final TransactionTemplate tx;
    private final ZoneId zone;

    public SapAsientoService(
            SgaRepository repo,
            SapAsientoClient client,
            CurrentUserProvider currentUser,
            PlatformTransactionManager transactionManager,
            @Value("${sga.ajustes.zone:America/Argentina/Buenos_Aires}") String zone) {
        this.repo = repo;
        this.client = client;
        this.currentUser = currentUser;
        this.tx = new TransactionTemplate(transactionManager);
        this.zone = ZoneId.of(zone);
    }

    public List<Map<String, Object>> enviar(List<Long> conciliacionIds) {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no tiene permiso para enviar asientos a SAP.");
        }
        if (!client.configurado()) {
            throw new SapNoConfiguradoException();
        }

        List<Map<String, Object>> resultados = new ArrayList<>();
        for (Long conciliacionId : conciliacionIds) {
            resultados.addAll(enviarConciliacion(conciliacionId));
        }
        return resultados;
    }

    private List<Map<String, Object>> enviarConciliacion(long conciliacionId) {
        Map<String, Object> cabecera = repo.queryOne("""
            SELECT co.id, co.estado AS estadoCodigo, i.nis
              FROM conciliacion co
              JOIN contrato c ON c.id = co.contrato_id
              JOIN inmueble i ON i.id = c.inmueble_id
             WHERE co.id = :id
            """, new MapSqlParameterSource("id", conciliacionId));
        if (cabecera == null) {
            return List.of(resultado(conciliacionId, null, null, null, false, null,
                    "Conciliación no encontrada."));
        }

        String nis = texto(cabecera.get("nis"));
        String estado = texto(cabecera.get("estadoCodigo"));
        if (!ENVIABLES.contains(estado)) {
            return List.of(resultado(conciliacionId, nis, null, null, false, null,
                    "La conciliación no está en estado OK u OK con diferencia."));
        }

        List<Map<String, Object>> facturas = repo.query("""
            SELECT f.id AS facturaId,
                   f.punto_venta AS puntoVenta,
                   f.numero_comprobante AS numero,
                   f.fecha_emision AS fechaEmision,
                   f.periodo_facturado AS periodo,
                   f.importe_total AS importe,
                   f.cae,
                   f.fecha_vto_cae AS fechaVtoCae,
                   f.moneda,
                   tc.codigo AS tipoCodigo,
                   c.ceco_sap AS cecoSap,
                   c.division_sap AS divisionSap,
                   c.cuenta_gasto AS cuentaGasto,
                   c.indicador_impuesto AS indicadorImpuesto,
                   sap.codigo_sap AS acreedorSap,
                   (
                       SELECT TOP 1 du.codigo
                         FROM inmueble_destino idd
                         JOIN destino_uso du ON du.id = idd.destino_id
                        WHERE idd.inmueble_id = c.inmueble_id
                   ) AS destinoCodigo
              FROM conciliacion_factura cf
              JOIN factura f ON f.id = cf.factura_id
              JOIN conciliacion co ON co.id = cf.conciliacion_id
              JOIN contrato c ON c.id = co.contrato_id
              LEFT JOIN tipo_comprobante tc ON tc.id = f.tipo_comprobante_id
              LEFT JOIN acreedor_sap sap ON sap.id = c.acreedor_sap_id
             WHERE cf.conciliacion_id = :id
             ORDER BY f.id
            """, new MapSqlParameterSource("id", conciliacionId));

        if (facturas.isEmpty()) {
            return List.of(resultado(conciliacionId, nis, null, null, false, null,
                    "La conciliación no tiene facturas vinculadas."));
        }

        List<Map<String, Object>> resultados = new ArrayList<>();
        for (Map<String, Object> factura : facturas) {
            resultados.add(enviarFactura(conciliacionId, nis, factura));
        }
        return resultados;
    }

    private Map<String, Object> enviarFactura(long conciliacionId, String nis, Map<String, Object> factura) {
        long facturaId = ((Number) factura.get("facturaId")).longValue();
        String comprobante = texto(factura.get("numero"));

        Integer yaEnviado = repo.jdbc().query("""
            SELECT TOP 1 numero
              FROM asiento_sap
             WHERE factura_id = :id AND estado = N'ENVIADO'
            """, new MapSqlParameterSource("id", facturaId), rs -> rs.next() ? rs.getInt(1) : null);
        if (yaEnviado != null) {
            return resultado(conciliacionId, nis, facturaId, comprobante, false, yaEnviado,
                    "La factura ya fue enviada a SAP (asiento " + yaEnviado + ").");
        }

        String faltantes = faltantes(factura);
        if (faltantes != null) {
            return resultado(conciliacionId, nis, facturaId, comprobante, false, null, faltantes);
        }

        String clase = texto(factura.get("tipoCodigo"));
        if (!"FA".equals(clase) && !"FC".equals(clase)) {
            return resultado(conciliacionId, nis, facturaId, comprobante, false, null,
                    "El tipo de comprobante no es Factura A ni Factura C.");
        }

        LocalDate envio = LocalDate.now(zone);
        LocalDate periodo = fecha(factura.get("periodo"));
        String letra = "FA".equals(clase) ? "A" : "C";
        AsientoContableXml.Datos datos = new AsientoContableXml.Datos(
                0,
                fecha(factura.get("fechaEmision")),
                clase,
                envio,
                moneda(factura.get("moneda")),
                AsientoContableXml.referencia(
                        ((Number) factura.get("puntoVenta")).intValue(),
                        letra,
                        texto(factura.get("numero"))),
                AsientoContableXml.texto(periodo, texto(factura.get("destinoCodigo"))),
                texto(factura.get("acreedorSap")),
                texto(factura.get("cuentaGasto")),
                texto(factura.get("divisionSap")),
                texto(factura.get("cecoSap")),
                texto(factura.get("indicadorImpuesto")),
                new BigDecimal(factura.get("importe").toString()),
                texto(factura.get("cae")),
                fecha(factura.get("fechaVtoCae"))
        );

        int numero = siguienteNumero();
        String xml = AsientoContableXml.build(new AsientoContableXml.Datos(
                numero,
                datos.fechaDoc(),
                datos.claseDoc(),
                datos.fechaCont(),
                datos.moneda(),
                datos.referencia(),
                datos.texto(),
                datos.acreedor(),
                datos.cuentaGasto(),
                datos.divisionGasto(),
                datos.ceco(),
                datos.indicadorImpuesto(),
                datos.importe(),
                datos.cae(),
                datos.fechaVtoCae()
        ));

        try {
            String respuesta = client.enviar(xml);
            guardar(numero, facturaId, conciliacionId, xml, "ENVIADO", respuesta);
            return resultado(conciliacionId, nis, facturaId, comprobante, true, numero, null);
        } catch (RuntimeException ex) {
            String mensaje = ex.getMessage() == null ? "Error al enviar el asiento a SAP." : ex.getMessage();
            guardar(numero, facturaId, conciliacionId, xml, "ERROR", mensaje);
            return resultado(conciliacionId, nis, facturaId, comprobante, false, numero, mensaje);
        }
    }

    private String faltantes(Map<String, Object> factura) {
        List<String> faltan = new ArrayList<>();
        if (factura.get("puntoVenta") == null) faltan.add("punto de venta");
        if (vacio(factura.get("numero"))) faltan.add("número de comprobante");
        if (fecha(factura.get("fechaEmision")) == null) faltan.add("fecha de emisión");
        if (fecha(factura.get("periodo")) == null) faltan.add("período facturado");
        if (vacio(factura.get("cae"))) faltan.add("CAE");
        if (fecha(factura.get("fechaVtoCae")) == null) faltan.add("vencimiento del CAE");
        if (factura.get("importe") == null) faltan.add("importe");
        if (vacio(factura.get("acreedorSap"))) faltan.add("código de acreedor SAP");
        if (vacio(factura.get("cecoSap"))) faltan.add("CeCo SAP");
        if (vacio(factura.get("divisionSap"))) faltan.add("división SAP");
        if (vacio(factura.get("cuentaGasto"))) faltan.add("cuenta de gasto");
        if (vacio(factura.get("indicadorImpuesto"))) faltan.add("indicador de impuestos");
        if (vacio(factura.get("destinoCodigo"))) faltan.add("destino de uso");
        if (faltan.isEmpty()) {
            return null;
        }
        return "Faltan datos para el asiento: " + String.join(", ", faltan) + ".";
    }

    private int siguienteNumero() {
        Integer numero = tx.execute(status -> {
            Integer actual = repo.jdbc().queryForObject("""
                SELECT ultimo
                  FROM asiento_secuencia WITH (UPDLOCK, HOLDLOCK)
                 WHERE id = 1
                """, new MapSqlParameterSource(), Integer.class);
            int siguiente = (actual == null ? 0 : actual) + 1;
            repo.jdbc().update(
                "UPDATE asiento_secuencia SET ultimo = :ultimo WHERE id = 1",
                new MapSqlParameterSource("ultimo", siguiente)
            );
            return siguiente;
        });
        if (numero == null) {
            throw new IllegalStateException("No se pudo obtener el número de asiento.");
        }
        return numero;
    }

    private void guardar(int numero, long facturaId, long conciliacionId, String xml, String estado, String respuesta) {
        tx.executeWithoutResult(status -> repo.jdbc().update("""
            INSERT INTO asiento_sap (numero, factura_id, conciliacion_id, xml_enviado, estado, respuesta)
            VALUES (:numero, :facturaId, :conciliacionId, :xml, :estado, :respuesta)
            """, new MapSqlParameterSource()
                .addValue("numero", numero)
                .addValue("facturaId", facturaId)
                .addValue("conciliacionId", conciliacionId)
                .addValue("xml", xml)
                .addValue("estado", estado)
                .addValue("respuesta", respuesta)
        ));
    }

    private static Map<String, Object> resultado(
            long conciliacionId,
            String nis,
            Long facturaId,
            String comprobante,
            boolean ok,
            Integer asiento,
            String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("conciliacionId", conciliacionId);
        row.put("nis", nis);
        row.put("facturaId", facturaId);
        row.put("comprobante", comprobante);
        row.put("ok", ok);
        row.put("asiento", asiento);
        row.put("error", error);
        return row;
    }

    private static String moneda(Object raw) {
        String valor = texto(raw);
        return valor == null ? "ARS" : valor;
    }

    private static boolean vacio(Object raw) {
        return texto(raw) == null;
    }

    private static String texto(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate fecha(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDate date) {
            return date;
        }
        if (raw instanceof Date date) {
            return date.toLocalDate();
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        String s = raw.toString().trim();
        if (s.length() < 10) {
            return null;
        }
        return LocalDate.parse(s.substring(0, 10));
    }
}
