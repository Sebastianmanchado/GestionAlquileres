package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReconciliationService {

    private static final Set<String> ANALISTA_ACTIONS = Set.of("aceptar", "rechazar", "reasignar", "ajuste", "agregar");
    private static final Set<String> SUPERVISOR_ACTIONS = Set.of("aprobar", "devolver");

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public ReconciliationService(SgaRepository repo, AuditService audit, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public List<Map<String, Object>> periodos() {
        return repo.query("SELECT DISTINCT periodo FROM conciliacion ORDER BY periodo DESC", new MapSqlParameterSource());
    }

    public Map<String, Object> list(String periodo) {
        LocalDate per = periodo == null || periodo.isBlank() ? latestPeriod() : LocalDate.parse(periodo.substring(0, 10));
        MapSqlParameterSource p = new MapSqlParameterSource("periodo", per);
        List<Map<String, Object>> rows = repo.query("""
            SELECT co.id, i.nis, i.denominacion AS denom,
                   co.importe_esperado AS esperado, co.importe_facturado AS facturado, co.diferencia,
                   co.estado AS estadoCodigo, c.id AS contratoId, c.cantidad_facturas as cantidad_facturas,
                   (
                        SELECT COUNT(*)
                        FROM factura f
                        WHERE f.contrato_id = c.id
                    ) AS facturas_existentes,
                   (SELECT TOP 1 f.numero_comprobante FROM conciliacion_factura cf JOIN factura f ON f.id=cf.factura_id
                     WHERE cf.conciliacion_id=co.id ORDER BY f.id) AS comprobante
              FROM conciliacion co
              JOIN contrato c ON c.id=co.contrato_id
              JOIN inmueble i ON i.id=c.inmueble_id
             WHERE co.periodo=:periodo
             ORDER BY i.nis
            """, p);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("periodo", per);
        out.put("periodos", periodos());

        List<Map<String, Object>> filtros = new ArrayList<>();
        for (String est : List.of("OK", "OK_CON_DIF", "CON_DIFERENCIA", "SIN_FACTURA")) {
            long count = rows.stream().filter(r -> est.equals(r.get("estadoCodigo"))).count();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("codigo", est);
            f.put("count", count);
            filtros.add(f);
        }
        out.put("filtros", filtros);
        return out;
    }

    @Transactional
    public int run(String periodo) {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no puede ejecutar la conciliación.");
        }
        LocalDate per = periodo == null || periodo.isBlank() ? latestPeriod() : LocalDate.parse(periodo.substring(0, 10));
        MapSqlParameterSource pp = new MapSqlParameterSource("periodo", per);

        List<Map<String, Object>> contratos = repo.query("""
            SELECT c.id AS contratoId,
                   (SELECT TOP 1 cv.importe_mensual FROM contrato_valor cv WHERE cv.contrato_id=c.id AND cv.vigencia_hasta IS NULL) AS esperado
              FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id
             WHERE e.codigo <> 'RESCINDIDO'
            """, new MapSqlParameterSource());

        int procesadas = 0;
        for (Map<String, Object> ct : contratos) {
            Long contratoId = ((Number) ct.get("contratoId")).longValue();
            java.math.BigDecimal esperado = (java.math.BigDecimal) ct.get("esperado");
            if (esperado == null) esperado = java.math.BigDecimal.ZERO;

            // se quito los filtros por periodo
            MapSqlParameterSource fp = new MapSqlParameterSource().addValue("c", contratoId);
            
            java.math.BigDecimal facturado = repo.jdbc().queryForObject(
                    "SELECT ISNULL(SUM(importe_total),0) FROM factura WHERE contrato_id=:c", fp, java.math.BigDecimal.class);
            int cnt = repo.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM factura WHERE contrato_id=:c", fp, Integer.class);

            String estado;
            if (cnt == 0) estado = "SIN_FACTURA";
            else if (facturado.compareTo(esperado) == 0) estado = "OK";
            else if (facturado.subtract(esperado).abs().compareTo(esperado.multiply(new java.math.BigDecimal("0.03"))) <= 0) estado = "OK_CON_DIF";
            else estado = "CON_DIFERENCIA";

            MapSqlParameterSource up = new MapSqlParameterSource()
                    .addValue("c", contratoId)
                    .addValue("esperado", esperado).addValue("facturado", facturado).addValue("estado", estado);
            int updated = repo.jdbc().update("""
                UPDATE conciliacion SET importe_esperado=:esperado, importe_facturado=:facturado, estado=:estado
                 WHERE contrato_id=:c
                """, up);
            if (updated == 0) {
                repo.jdbc().update("""
                    INSERT INTO conciliacion (contrato_id, periodo, importe_esperado, importe_facturado, estado)
                    VALUES (:c, :periodo, :esperado, :facturado, :estado)
                    """, up);
            }
            procesadas++;
        }
        
        audit.log("conciliacion", per.toString(), "EJECUTAR", "Ejecutó la conciliación del período " + per, "—", null, null);
        return procesadas;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> run2(String periodo) {

        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException(
                "El rol actual no puede ejecutar la conciliación."
            );
        }

        LocalDate per = periodo == null || periodo.isBlank()
            ? latestPeriod()
            : LocalDate.parse(periodo.substring(0, 10));

        return repo.query(
            """
            SELECT
                c.id,
                c.contrato_id AS contratoId,
                c.periodo,
                c.importe_esperado AS esperado,
                c.importe_facturado AS facturado,
                c.diferencia,
                c.estado,
                c.comentario,
                c.revisada_por AS revisadaPor,
                c.revisada_en AS revisadaEn
            FROM conciliacion c
            WHERE c.periodo = :periodo
            ORDER BY c.contrato_id
            """,
            new MapSqlParameterSource()
                .addValue("periodo", per)
        );
    }

    public Map<String, Object> detail(long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        Map<String, Object> conc = repo.queryOne("""
            SELECT co.id, co.periodo, co.importe_esperado AS esperado, co.importe_facturado AS facturado,
                   co.diferencia, co.estado AS estadoCodigo, co.comentario, co.revisada_en AS revisadaEn,
                   i.nis, i.denominacion AS denom, lo.cuit AS locadorCuit,
                   tcc.nombre AS contratoTipoComprobante,
                   u.nombre AS revisadaPor,
                   (SELECT TOP 1 rol FROM usuario_rol_cache WHERE usuario_id=u.id) AS revisadaRol
              FROM conciliacion co
              JOIN contrato c ON c.id=co.contrato_id
              JOIN inmueble i ON i.id=c.inmueble_id
              JOIN locador lo ON lo.id=c.locador_id
              LEFT JOIN tipo_comprobante tcc ON tcc.id=c.tipo_comprobante_id
              LEFT JOIN usuario u ON u.id=co.revisada_por
             WHERE co.id=:id
            """, p);
        if (conc == null) throw new NotFoundException("Conciliación no encontrada");

        Map<String, Object> factura = repo.queryOne("""
            SELECT TOP 1 f.id, f.numero_comprobante AS numero, f.importe_total AS importe, f.cuit_emisor AS cuit,
                   f.periodo_facturado AS periodo, f.cae, f.estado AS estadoCodigo, f.archivo_id AS archivoId,
                   tc.nombre AS tipoComprobante
              FROM conciliacion_factura cf JOIN factura f ON f.id=cf.factura_id
              LEFT JOIN tipo_comprobante tc ON tc.id=f.tipo_comprobante_id
             WHERE cf.conciliacion_id=:id
             ORDER BY CASE WHEN f.estado='CON_DIFERENCIA' THEN 0 ELSE 1 END, f.id
            """, p);

        List<Map<String, Object>> diffFields = new ArrayList<>();
        Object esperado = conc.get("esperado");
        Object facturado = factura == null ? conc.get("facturado") : factura.get("importe");
        diffFields.add(diff("Importe", fmt(esperado), fmt(facturado), !eq(esperado, facturado)));
        String cuitContrato = conc.get("locadorCuit") == null ? "—" : conc.get("locadorCuit").toString();
        String cuitFactura = factura == null ? "—" : String.valueOf(factura.get("cuit"));
        diffFields.add(diff("CUIT emisor", cuitContrato, cuitFactura, !cuitContrato.equals(cuitFactura)));
        String tcContrato = String.valueOf(conc.getOrDefault("contratoTipoComprobante", "—"));
        String tcFactura = factura == null ? "—" : String.valueOf(factura.get("tipoComprobante"));
        diffFields.add(diff("Tipo de comprobante", tcContrato, tcFactura, !tcContrato.equals(tcFactura)));
        String perContrato = String.valueOf(conc.get("periodo"));
        String perFactura = factura == null ? "—" : String.valueOf(factura.get("periodo"));
        diffFields.add(diff("Período", perContrato, perFactura, !perContrato.equals(perFactura)));
        String caeFactura = factura == null || factura.get("cae") == null ? "—" : String.valueOf(factura.get("cae"));
        diffFields.add(diff("CAE", "—", caeFactura, false));

        conc.put("diffFields", diffFields);
        conc.put("factura", factura);
        conc.put("diferenciasRegistradas", repo.query("""
            SELECT tipo, severidad, valor_esperado AS valorEsperado, valor_obtenido AS valorObtenido
              FROM conciliacion_diferencia WHERE conciliacion_id=:id
            """, p));
        return conc;
    }

    @Transactional
    public void action(long id, String action, String comentario) {
        String act = action == null ? "" : action.trim().toLowerCase();
        var role = currentUser.currentRole();
        if (role.isReadOnly()) throw new ForbiddenException("El rol Auditor no puede tomar acciones.");
        if (ANALISTA_ACTIONS.contains(act) && !role.canEdit()) throw new ForbiddenException("Acción no permitida para el rol actual.");
        if (SUPERVISOR_ACTIONS.contains(act) && !role.canApprove()) throw new ForbiddenException("Acción no permitida para el rol actual.");
        if (!ANALISTA_ACTIONS.contains(act) && !SUPERVISOR_ACTIONS.contains(act)) throw new IllegalArgumentException("Acción desconocida: " + action);

        Map<String, Object> conc = repo.queryOne("SELECT co.id, i.nis, i.denominacion AS denom FROM conciliacion co JOIN contrato c ON c.id=co.contrato_id JOIN inmueble i ON i.id=c.inmueble_id WHERE co.id=:id",
                new MapSqlParameterSource("id", id));
        if (conc == null) throw new NotFoundException("Conciliación no encontrada");

        Long usuarioId = repo.jdbc().queryForObject("SELECT id FROM usuario WHERE username=:u",
                new MapSqlParameterSource("u", currentUser.currentUsername()), Long.class);
        repo.jdbc().update("""
            UPDATE conciliacion SET comentario = COALESCE(:comentario, comentario), revisada_por=:usuario, revisada_en=SYSUTCDATETIME()
             WHERE id=:id
            """, new MapSqlParameterSource().addValue("comentario", comentario).addValue("usuario", usuarioId).addValue("id", id));

        String label = switch (act) {
            case "aceptar" -> "Aceptó diferencia con justificación";
            case "rechazar" -> "Rechazó la factura";
            case "reasignar" -> "Reasignó factura a otro contrato";
            case "ajuste" -> "Generó ajuste";
            case "agregar" -> "Agregó factura";
            case "aprobar" -> "Aprobó resolución del analista";
            case "devolver" -> "Devolvió diferencia al analista";
            default -> "Acción sobre conciliación";
        };
        String ref = conc.get("nis") + " · " + conc.get("denom");
        audit.log("conciliacion", String.valueOf(id), act.toUpperCase(), label, ref, null, null);
    }

    private LocalDate latestPeriod() {
        LocalDate p = repo.jdbc().queryForObject("SELECT MAX(periodo) FROM conciliacion", new MapSqlParameterSource(), LocalDate.class);
        return p == null ? LocalDate.now().withDayOfMonth(1) : p;
    }

    private Map<String, Object> diff(String label, String contrato, String factura, boolean isDiff) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("contrato", contrato);
        m.put("factura", factura);
        m.put("diff", isDiff);
        return m;
    }

    private boolean eq(Object a, Object b) {
        if (a == null || b == null) return a == b;
        return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString())) == 0;
    }

    private String fmt(Object o) {
        return o == null ? "—" : o.toString();
    }
}
