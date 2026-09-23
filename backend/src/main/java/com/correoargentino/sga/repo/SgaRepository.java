package com.correoargentino.sga.repo;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceso a datos con Spring JDBC. Devuelve estructuras "listas para JSON"
 * (Map/List) que reflejan exactamente lo que necesita cada pantalla.
 */
@Repository
public class SgaRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private static final int ESTADO_VIGENTE     = 1;
    private static final int ESTADO_VENCIDO     = 3;
    private static final int ESTADO_RESCINDIDO  = 4;

    private static final Map<String, String> CONTRACT_SORT = Map.ofEntries(
            Map.entry("nis", "i.nis"),
            Map.entry("denom", "i.denominacion"),
            Map.entry("region", "r.nombre"),
            Map.entry("localidad", "l.nombre"),
            Map.entry("provincia", "p.nombre"),
            Map.entry("destino", "destino"),
            Map.entry("valorActual", "valorActual"),
            Map.entry("indice", "ia.codigo"),
            Map.entry("tipo", "tc.nombre"),
            Map.entry("inicio", "c.fecha_inicio"),
            Map.entry("vencimiento", "c.fecha_vencimiento"),
            Map.entry("estado", "ec.nombre"),
            Map.entry("propietario", "lo.razon_social")
    );

    private static final Map<String, String> INMUEBLE_SORT = Map.ofEntries(
            Map.entry("nis", "i.nis"),
            Map.entry("denom", "i.denominacion"),
            Map.entry("region", "r.nombre"),
            Map.entry("localidad", "l.nombre"),
            Map.entry("provincia", "p.nombre"),
            Map.entry("direccion", "i.direccion"),
            Map.entry("destino", "d.destino"),
            Map.entry("superficie", "i.superficie_cubierta_m2")
    );

    public SgaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ================= helpers ================= */

    /** RowMapper que normaliza los tipos JDBC a tipos amigables para Jackson. */
    private static final RowMapper<Map<String, Object>> ROW = (rs, rowNum) -> {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= cols; i++) {
            String key = md.getColumnLabel(i);
            row.put(key, normalize(rs, i));
        }
        return row;
    };

    private static Object normalize(ResultSet rs, int i) throws SQLException {
        Object v = rs.getObject(i);
        if (v == null) return null;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return v;
    }

    public List<Map<String, Object>> query(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, ROW);
    }

    public Map<String, Object> queryOne(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> list = query(sql, params);
        return list.isEmpty() ? null : list.get(0);
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    /* ================= Contratos: listado ================= */

    private static final String CONTRACT_SELECT = """
        SELECT c.id,
               i.nis,
               i.denominacion                         AS denom,
               i.responsable                          AS responsable,
               r.nombre                               AS region,
               l.nombre                               AS localidad,
               p.nombre                               AS provincia,
               (SELECT STRING_AGG(du.nombre, ' + ')
                  FROM inmueble_destino idd
                  JOIN destino_uso du ON du.id = idd.destino_id
                 WHERE idd.inmueble_id = i.id)         AS destino,
               i.superficie_cubierta_m2               AS m2,
               (SELECT TOP 1 cv.importe_mensual FROM contrato_valor cv
                 WHERE cv.contrato_id = c.id AND cv.vigencia_hasta IS NULL) AS valorActual,
               ia.codigo                              AS indice,
               tc.nombre                              AS tipo,
               c.fecha_inicio                         AS inicio,
               c.fecha_vencimiento                    AS vencimiento,
               ec.codigo                              AS estadoCodigo,
               ec.nombre                              AS estadoNombre,
               lo.razon_social                        AS propietario,
               lo.cuit                                 AS locadorCuit
          FROM contrato c
          JOIN inmueble i          ON i.id = c.inmueble_id
          LEFT JOIN region r       ON r.id = i.region_id
          LEFT JOIN localidad l    ON l.id = i.localidad_id
          LEFT JOIN provincia p    ON p.id = l.provincia_id
          JOIN tipo_contrato tc    ON tc.id = c.tipo_contrato_id
          JOIN estado_contrato ec  ON ec.id = c.estado_contrato_id
          JOIN locador lo          ON lo.id = c.locador_id
          LEFT JOIN indice_ajuste ia ON ia.id = c.indice_ajuste_id
         WHERE ec.id <> 4 /*FILTERS*/
        """;

    private int recalcularEstadosContrato() {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("hoy",        java.sql.Date.valueOf(LocalDate.now()))
                .addValue("vigente",    ESTADO_VIGENTE)
                .addValue("vencido",    ESTADO_VENCIDO)
                .addValue("rescindido", ESTADO_RESCINDIDO);

        String sql = """
                UPDATE c
                SET c.estado_contrato_id =
                        CASE
                            WHEN c.fecha_vencimiento < :hoy THEN :vencido
                            ELSE :vigente
                        END
                FROM contrato c
                WHERE c.estado_contrato_id <> :rescindido
                AND c.fecha_vencimiento IS NOT NULL
                AND c.estado_contrato_id <>
                        CASE
                            WHEN c.fecha_vencimiento < :hoy THEN :vencido
                            ELSE :vigente
                        END
                """;

        return jdbc.update(sql, p);
    }

    public Map<String, Object> listContracts(String search, String region, String estado,
                                            String indice, String venc, int page, int size,
                                            String sort, String dir) {

        int estadosActualizados = recalcularEstadosContrato();

        StringBuilder where = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            String q = search.trim();
            where.append("""
                 AND (
                      i.nis LIKE :search
                   OR i.denominacion LIKE :search
                   OR ISNULL(i.responsable,'') LIKE :search
                   OR lo.razon_social LIKE :search
                   OR lo.cuit LIKE :search
            """);
            p.addValue("search", "%" + q + "%");
            String digits = q.replaceAll("[^0-9]", "");
            if (digits.length() >= 3) {
                where.append(" OR lo.cuit LIKE :searchCuit");
                p.addValue("searchCuit", "%" + digits + "%");
            }
            where.append(")");
        }
        if (region != null && !region.isBlank() && !region.startsWith("Región")) {
            where.append(" AND r.nombre = :region");
            p.addValue("region", region.trim());
        }
        if (estado != null && !estado.isBlank()) {
            where.append(" AND ec.codigo = :estado");
            p.addValue("estado", estado.trim());
        }
        if (indice != null && !indice.isBlank()) {
            where.append(" AND ia.codigo = :indice");
            p.addValue("indice", indice.trim());
        }
        if (venc != null && !venc.isBlank()) {
            switch (venc) {
                case "30" -> {
                    where.append(" AND c.fecha_vencimiento BETWEEN :hoyF AND DATEADD(DAY,30,:hoyF)");
                    p.addValue("hoyF", java.sql.Date.valueOf(LocalDate.now()));
                }
                case "90" -> {
                    where.append(" AND c.fecha_vencimiento BETWEEN :hoyF AND DATEADD(DAY,90,:hoyF)");
                    p.addValue("hoyF", java.sql.Date.valueOf(LocalDate.now()));
                }
                case "vencidos" -> {
                    where.append(" AND c.fecha_vencimiento < :hoyF");
                    p.addValue("hoyF", java.sql.Date.valueOf(LocalDate.now()));
                }
                default -> { }
            }
        }

        String base = "FROM contrato c " +
                "JOIN inmueble i ON i.id=c.inmueble_id " +
                "LEFT JOIN region r ON r.id=i.region_id " +
                "LEFT JOIN localidad l ON l.id=i.localidad_id " +
                "LEFT JOIN provincia p ON p.id=l.provincia_id " +
                "JOIN tipo_contrato tc ON tc.id=c.tipo_contrato_id " +
                "JOIN estado_contrato ec ON ec.id=c.estado_contrato_id " +
                "JOIN locador lo ON lo.id=c.locador_id " +
                "LEFT JOIN indice_ajuste ia ON ia.id=c.indice_ajuste_id " +
                "WHERE ec.id <> :rescindido" + where;

        p.addValue("rescindido", ESTADO_RESCINDIDO);

        Integer total = jdbc.queryForObject("SELECT COUNT(*) " + base, p, Integer.class);

        String dataSql = CONTRACT_SELECT.replace("/*FILTERS*/", where.toString()) +
                " ORDER BY " + orderBy(sort, dir, CONTRACT_SORT, "i.nis") +
                " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
        p.addValue("offset", page * size);
        p.addValue("size", size);

        List<Map<String, Object>> rows = query(dataSql, p);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        result.put("estadosActualizados", estadosActualizados);
        return result;
    }

    /* ================= Contrato: detalle ================= */

    public Map<String, Object> getContractDetail(long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        Map<String, Object> head = queryOne("""
            SELECT c.id, c.numero, i.id AS inmuebleId, i.nis, i.denominacion AS denom,
                   i.region_id AS regionId, i.localidad_id AS localidadId,
                   (SELECT TOP 1 idd.destino_id FROM inmueble_destino idd WHERE idd.inmueble_id = i.id) AS destinoId,
                   i.direccion, l.nombre AS localidad, p.nombre AS provincia, r.nombre AS region,
                   du.destino AS destino,
                   i.superficie_cubierta_m2 AS supCubierta, i.superficie_terreno_m2 AS supTerreno,
                   i.responsable, i.responsable_email AS responsableEmail, i.responsable_telefono AS responsableTelefono,
                   ec.codigo AS estadoCodigo, ec.nombre AS estadoNombre,
                   tc.nombre AS tipo,
                   c.fecha_inicio AS inicio, c.fecha_vencimiento AS vencimiento,
                   c.moneda, c.importe_inicial AS importeInicial, c.deposito_garantia AS deposito,
                   c.periodicidad_ajuste AS periodicidad, c.tolerancia_importe_pct AS tolerancia, c.locador_id AS locadorId, c.indice_ajuste_id AS indiceId, c.tipo_facturacion as tipoFacturacion, c.tipo_contrato_id AS tipoContratoId,
                   ia.codigo AS indiceCodigo, ia.nombre AS indiceNombre,
                   lo.razon_social AS locadorRazon, lo.cuit AS locadorCuit, lo.email AS locadorEmail, lo.telefono AS locadorTelefono,
                   sap.codigo_sap AS acreedorSap,
                   ant.numero AS contratoAnteriorNumero, ant.id AS contratoAnteriorId
              FROM contrato c
              JOIN inmueble i ON i.id=c.inmueble_id
              LEFT JOIN region r ON r.id=i.region_id
              LEFT JOIN localidad l ON l.id=i.localidad_id
              LEFT JOIN provincia p ON p.id=l.provincia_id
              LEFT JOIN (SELECT idd.inmueble_id, STRING_AGG(d.nombre,' + ') AS destino
                           FROM inmueble_destino idd JOIN destino_uso d ON d.id=idd.destino_id
                          GROUP BY idd.inmueble_id) du ON du.inmueble_id=i.id
              JOIN estado_contrato ec ON ec.id=c.estado_contrato_id
              JOIN tipo_contrato tc ON tc.id=c.tipo_contrato_id
              LEFT JOIN indice_ajuste ia ON ia.id=c.indice_ajuste_id
              JOIN locador lo ON lo.id=c.locador_id
              LEFT JOIN acreedor_sap sap ON sap.id=c.acreedor_sap_id
              LEFT JOIN contrato ant ON ant.id=c.contrato_anterior_id
             WHERE c.id=:id
            """, p);
        if (head == null) return null;

        head.put(
            "facturas_planificadas",
            query(
                """
                SELECT
                    fp.id,
                    fp.porcentaje_esperado AS porcentaje,
                    fp.monto_esperado AS monto,
                    fp.estado
                FROM factura_planificada fp
                WHERE fp.contrato_id = :id
                ORDER BY fp.id
                """,
                p
            )
        );

        Object valorActual = jdbc.query("SELECT TOP 1 importe_mensual FROM contrato_valor WHERE contrato_id=:id AND vigencia_hasta IS NULL",
                p, (rs) -> rs.next() ? rs.getBigDecimal(1) : null);
        head.put("valorActual", valorActual);

        head.put("seguro", queryOne("""
            SELECT tipo, nro_poliza AS poliza, suma_asegurada AS suma, vigencia_hasta AS vigencia
              FROM contrato_seguro WHERE contrato_id=:id
            """, p));

        head.put("valueHistory", query("""
            SELECT vigencia_desde AS desde, vigencia_hasta AS hasta, importe_mensual AS importe,
                   origen, coeficiente_aplicado AS coeficiente,
                   (SELECT codigo FROM indice_ajuste WHERE id = cv.indice_id) AS indice
              FROM contrato_valor cv WHERE contrato_id=:id ORDER BY vigencia_desde DESC
            """, p));

        head.put("facturas", query("""
            SELECT f.id, f.fecha_emision AS fecha, f.numero_comprobante AS numero,
                   f.importe_total AS importe, f.periodo_facturado AS periodo, f.estado AS estadoCodigo
              FROM factura f WHERE f.contrato_id=:id ORDER BY f.periodo_facturado DESC, f.id DESC
            """, p));

        head.put("documents", query("""
            SELECT
                a.id,
                a.nombre_original AS nombre,
                'FACTURA' AS tipo,
                a.creado_en AS fecha
            FROM factura f
            JOIN archivo a ON a.factura_id = f.id
            WHERE f.contrato_id = :id
            ORDER BY a.creado_en DESC
            """, p));

        head.put("changeLog", query("""
            SELECT au.fecha, u.nombre AS usuario, au.descripcion AS campo,
                   au.datos_antes AS anterior, au.datos_despues AS nuevo
              FROM auditoria au LEFT JOIN usuario u ON u.id=au.usuario_id
             WHERE au.entidad='contrato' AND au.entidad_id=CAST(:id AS NVARCHAR(60)) AND au.datos_antes IS NOT NULL
             ORDER BY au.fecha DESC
            """, p));

        return head;
    }

    /* ================= Catálogos (para formularios) ================= */

    public Map<String, Object> catalogs() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("regiones", query("SELECT id, nombre FROM region WHERE activo=1 ORDER BY id", new MapSqlParameterSource()));
        c.put("provincias", query("SELECT id, nombre FROM provincia ORDER BY nombre", new MapSqlParameterSource()));
        c.put("localidades", query("SELECT id, provincia_id AS provinciaId, nombre FROM localidad ORDER BY nombre", new MapSqlParameterSource()));
        c.put("destinos", query("SELECT id, nombre FROM destino_uso ORDER BY id", new MapSqlParameterSource()));
        c.put("tiposContrato", query("SELECT id, nombre FROM tipo_contrato ORDER BY id", new MapSqlParameterSource()));
        c.put("estados", query("SELECT id, codigo, nombre FROM estado_contrato ORDER BY id", new MapSqlParameterSource()));
        c.put("indices", query("SELECT id, codigo, nombre FROM indice_ajuste ORDER BY id", new MapSqlParameterSource()));
        c.put("tiposComprobante", query("SELECT id, codigo, nombre FROM tipo_comprobante ORDER BY id", new MapSqlParameterSource()));
        c.put("locadores", query("SELECT id, razon_social AS razonSocial, cuit FROM locador WHERE activo=1 ORDER BY razon_social", new MapSqlParameterSource()));
        c.put("centrosCosto", query("SELECT id, codigo, descripcion FROM centro_costo ORDER BY id", new MapSqlParameterSource()));
        return c;
    }

    /* ================= Inmuebles ================= */

    public Map<String, Object> listInmuebles(String search, String region, int page, int size,
                                             String sort, String dir) {
        StringBuilder where = new StringBuilder(" WHERE i.activo = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            where.append("""
                 AND (
                      i.nis LIKE :search
                   OR i.denominacion LIKE :search
                   OR ISNULL(r.nombre,'') LIKE :search
                 )
            """);
            p.addValue("search", "%" + search.trim() + "%");
        }
        if (region != null && !region.isBlank() && !region.startsWith("Región")) {
            where.append(" AND r.nombre = :region");
            p.addValue("region", region.trim());
        }

        String from = """
            FROM inmueble i
            LEFT JOIN region r ON r.id = i.region_id
            LEFT JOIN localidad l ON l.id = i.localidad_id
            LEFT JOIN provincia p ON p.id = l.provincia_id
            OUTER APPLY (
                SELECT TOP 1 du.nombre AS destino
                  FROM inmueble_destino idd
                  JOIN destino_uso du ON du.id = idd.destino_id
                 WHERE idd.inmueble_id = i.id
            ) d
            """ + where;

        Integer total = jdbc.queryForObject("SELECT COUNT(*) " + from, p, Integer.class);

        String sql = """
            SELECT i.id,
                   i.nis,
                   i.denominacion AS denom,
                   r.nombre AS region,
                   l.nombre AS localidad,
                   p.nombre AS provincia,
                   i.direccion,
                   d.destino,
                   i.superficie_cubierta_m2 AS superficie
            """ + from + " ORDER BY " + orderBy(sort, dir, INMUEBLE_SORT, "i.nis") +
                " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
        p.addValue("offset", page * size);
        p.addValue("size", size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", query(sql, p));
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getInmueble(long id) {
        return queryOne("""
            SELECT i.id,
                   i.nis,
                   i.denominacion AS denominacion,
                   i.direccion,
                   i.region_id AS regionId,
                   r.nombre AS region,
                   i.localidad_id AS localidadId,
                   l.nombre AS localidad,
                   p.nombre AS provincia,
                   i.superficie_cubierta_m2 AS superficieCubierta,
                   d.destinoId,
                   d.destino
              FROM inmueble i
              LEFT JOIN region r ON r.id = i.region_id
              LEFT JOIN localidad l ON l.id = i.localidad_id
              LEFT JOIN provincia p ON p.id = l.provincia_id
              OUTER APPLY (
                  SELECT TOP 1 idd.destino_id AS destinoId, du.nombre AS destino
                    FROM inmueble_destino idd
                    JOIN destino_uso du ON du.id = idd.destino_id
                   WHERE idd.inmueble_id = i.id
              ) d
             WHERE i.id = :id
            """, new MapSqlParameterSource("id", id));
    }

    private static String orderBy(String sort, String dir, Map<String, String> allowed, String fallback) {
        String column = allowed.get(sort == null ? "" : sort.trim());
        if (column == null) column = fallback;
        String direction = "desc".equalsIgnoreCase(dir) ? "DESC" : "ASC";
        return column + " " + direction;
    }
}
