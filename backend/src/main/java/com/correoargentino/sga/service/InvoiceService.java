package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceService {

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public InvoiceService(SgaRepository repo, AuditService audit, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    private void requireEdit() {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no puede modificar facturas.");
        }
    }

    public List<Map<String, Object>> unassigned() {
        List<Map<String, Object>> rows = repo.query("""
            SELECT f.id, f.cuit_emisor AS cuit, f.razon_social AS razonSocial,
                   f.importe_total AS importe, f.periodo_facturado AS periodo, f.numero_comprobante AS comprobante
              FROM factura f WHERE f.estado='SIN_ASIGNAR' ORDER BY f.periodo_facturado DESC, f.id
            """, new MapSqlParameterSource());
        for (Map<String, Object> r : rows) {
            MapSqlParameterSource p = new MapSqlParameterSource("cuit", r.get("cuit"));
            List<Map<String, Object>> sug = repo.query("""
                SELECT TOP 5 i.nis, i.denominacion AS sucursal,
                       (SELECT TOP 1 cv.importe_mensual FROM contrato_valor cv WHERE cv.contrato_id=c.id AND cv.vigencia_hasta IS NULL) AS monto,
                       c.id AS contratoId
                  FROM contrato c
                  JOIN inmueble i ON i.id=c.inmueble_id
                  JOIN locador lo ON lo.id=c.locador_id
                  JOIN estado_contrato e ON e.id=c.estado_contrato_id
                 WHERE lo.cuit=:cuit AND e.codigo<>'RESCINDIDO'
                 ORDER BY i.nis
                """, p);
            r.put("sugerencias", sug);
        }
        return rows;
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> f = repo.queryOne("""
            SELECT f.id, f.cuit_emisor AS cuit, f.razon_social AS razonSocial, f.numero_comprobante AS comprobante,
                   f.importe_total AS importe, f.importe_neto AS neto, f.importe_iva AS iva,
                   f.periodo_facturado AS periodo, f.fecha_emision AS fechaEmision, f.cae, f.estado AS estadoCodigo,
                   f.punto_venta AS puntoVenta, f.observaciones, f.archivo_id AS archivoId,
                   tc.nombre AS tipoComprobante, tc.id AS tipoComprobanteId,
                   c.id AS contratoId, i.nis AS contratoNis, i.denominacion AS contratoDenom
              FROM factura f
              LEFT JOIN tipo_comprobante tc ON tc.id=f.tipo_comprobante_id
              LEFT JOIN contrato c ON c.id=f.contrato_id
              LEFT JOIN inmueble i ON i.id=c.inmueble_id
             WHERE f.id=:id
            """, new MapSqlParameterSource("id", id));
        if (f == null) throw new NotFoundException("Factura no encontrada");
        return f;
    }

    @Transactional
    public long create(Map<String, Object> body) {
        requireEdit();
        BigDecimal total = asDecimal(body.getOrDefault("importe", 0));
        BigDecimal neto = total == null ? null : total.divide(new BigDecimal("1.21"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal iva = total == null || neto == null ? null : total.subtract(neto);
        KeyHolder kh = new GeneratedKeyHolder();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cuit", str(body.getOrDefault("cuit", "00000000000")).replace("-", ""))
                .addValue("razon", str(body.get("razonSocial")))
                .addValue("comprobante", str(body.get("comprobante")))
                .addValue("total", total).addValue("neto", neto).addValue("iva", iva)
                .addValue("periodo", asDate(body.get("periodo")))
                .addValue("fechaEmision", asDate(body.get("fechaEmision")))
                .addValue("tipoComp", asInt(body.getOrDefault("tipoComprobanteId", 1)))
                .addValue("obs", str(body.get("observaciones")));
        repo.jdbc().update("""
            INSERT INTO factura (cuit_emisor, razon_social, numero_comprobante, importe_total, importe_neto, importe_iva,
                                 periodo_facturado, fecha_emision, tipo_comprobante_id, estado, origen, observaciones)
            VALUES (:cuit, :razon, :comprobante, :total, :neto, :iva, :periodo, :fechaEmision, :tipoComp, 'SIN_ASIGNAR', 'MANUAL', :obs)
            """, p, kh, new String[]{"id"});
        long id = kh.getKey().longValue();
        audit.log("factura", String.valueOf(id), "CREAR", "Creó una factura manual", "—", null, null);
        return id;
    }

    @Transactional
    public void update(long id, Map<String, Object> body) {
        requireEdit();
        get(id);
        BigDecimal total = body.get("importe") == null ? null : asDecimal(body.get("importe"));
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("cuit", body.get("cuit") == null ? null : str(body.get("cuit")).replace("-", ""))
                .addValue("razon", str(body.get("razonSocial")))
                .addValue("comprobante", str(body.get("comprobante")))
                .addValue("total", total)
                .addValue("periodo", body.get("periodo") == null ? null : asDate(body.get("periodo")))
                .addValue("fechaEmision", body.get("fechaEmision") == null ? null : asDate(body.get("fechaEmision")))
                .addValue("obs", str(body.get("observaciones")));
        repo.jdbc().update("""
            UPDATE factura SET
                cuit_emisor = COALESCE(:cuit, cuit_emisor),
                razon_social = COALESCE(:razon, razon_social),
                numero_comprobante = COALESCE(:comprobante, numero_comprobante),
                importe_total = COALESCE(:total, importe_total),
                periodo_facturado = COALESCE(:periodo, periodo_facturado),
                fecha_emision = COALESCE(:fechaEmision, fecha_emision),
                observaciones = COALESCE(:obs, observaciones)
             WHERE id=:id
            """, p);
        audit.log("factura", String.valueOf(id), "EDITAR", "Editó la factura", "—", null, null);
    }

    @Transactional
    public void assign(long id, long contratoId) {
        requireEdit();
        Map<String, Object> contrato = repo.queryOne(
                "SELECT c.id, c.inmueble_id AS inmuebleId, i.nis, i.denominacion AS denom FROM contrato c JOIN inmueble i ON i.id=c.inmueble_id WHERE c.id=:c",
                new MapSqlParameterSource("c", contratoId));
        if (contrato == null) throw new NotFoundException("Contrato no encontrado");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("contratoId", contratoId)
                .addValue("inmuebleId", contrato.get("inmuebleId"));
        repo.jdbc().update("""
            UPDATE factura SET contrato_id=:contratoId, inmueble_id=:inmuebleId, estado='PENDIENTE'
             WHERE id=:id
            """, p);
        String ref = contrato.get("nis") + " · " + contrato.get("denom");
        audit.log("factura", String.valueOf(id), "ASIGNAR", "Asignó factura al contrato", ref, null, null);
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Integer.parseInt(s);
    }

    private static BigDecimal asDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = o.toString().replaceAll("[^0-9,.-]", "").replace(".", "").replace(",", ".");
        if (s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
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

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
