package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InvoiceService {

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final NotificationService notificationService;

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    public InvoiceService(SgaRepository repo, AuditService audit, CurrentUserProvider currentUser, NotificationService notificationService) {
        this.repo = repo;
        this.audit = audit;
        this.currentUser = currentUser;
        this.notificationService = notificationService;
    }

    private void requireEdit() {
        log.info(currentUser.currentRole().name());
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no puede modificar facturas.");
        }
    }

    public List<Map<String, Object>> planificadas(){
        List<Map<String, Object>> facturas = repo.query(
            """
            SELECT
                id,
                contrato_id,
                porcentaje_esperado,
                monto_esperado,
                estado
            FROM factura_planificada
            """, new MapSqlParameterSource()
        );
        return facturas;
    }

    public List<Map<String, Object>> unassigned() {

        List<Map<String, Object>> rows = repo.query("""
            SELECT
                f.id,
                f.cuit_emisor AS cuit,
                f.razon_social AS razonSocial,
                f.importe_total AS importe,
                f.periodo_facturado AS periodo,
                f.numero_comprobante AS comprobante
            FROM factura f
            WHERE f.estado = 'SIN_ASIGNAR'
            ORDER BY f.periodo_facturado DESC, f.id
            """,
            new MapSqlParameterSource()
        );

        for (Map<String, Object> r : rows) {

            MapSqlParameterSource p =
                new MapSqlParameterSource("cuit", r.get("cuit"));

            List<Map<String, Object>> sug = repo.query("""
                SELECT TOP 8
                    i.nis,
                    i.denominacion AS sucursal,
                    i.responsable AS responsable,
                    lo.cuit AS locadorCuit,
                    lo.razon_social AS locadorRazon,
                    (
                        SELECT TOP 1 cv.importe_mensual
                        FROM contrato_valor cv
                        WHERE cv.contrato_id = c.id
                        AND cv.vigencia_hasta IS NULL
                    ) AS monto,
                    c.id AS contratoId
                FROM contrato c
                JOIN inmueble i
                    ON i.id = c.inmueble_id
                JOIN locador lo
                    ON lo.id = c.locador_id
                JOIN estado_contrato e
                    ON e.id = c.estado_contrato_id
                WHERE lo.cuit = :cuit
                AND e.codigo <> 'RESCINDIDO'
                ORDER BY i.nis
                """,
                p
            );

            if (sug.isEmpty()) {

                sug = repo.query("""
                    SELECT
                        i.nis,
                        i.denominacion AS sucursal,
                        i.responsable AS responsable,
                        lo.cuit AS locadorCuit,
                        lo.razon_social AS locadorRazon,
                        (
                            SELECT TOP 1 cv.importe_mensual
                            FROM contrato_valor cv
                            WHERE cv.contrato_id = c.id
                            AND cv.vigencia_hasta IS NULL
                        ) AS monto,
                        c.id AS contratoId
                    FROM contrato c
                    JOIN inmueble i
                        ON i.id = c.inmueble_id
                    JOIN locador lo
                        ON lo.id = c.locador_id
                    JOIN estado_contrato e
                        ON e.id = c.estado_contrato_id
                    WHERE e.codigo <> 'RESCINDIDO'
                    ORDER BY i.nis
                    """,
                    new MapSqlParameterSource()
                );
            }

            r.put("sugerencias", sug);
        }

        return rows;
    }


    public Map<String, Object> get(long id) {
        Map<String, Object> f = repo.queryOne("""
            SELECT f.id, f.cuit_emisor AS cuit, f.razon_social AS razonSocial, f.numero_comprobante AS comprobante,
                   f.importe_total AS importe, f.importe_neto AS neto, f.importe_iva AS iva,
                   f.periodo_facturado AS periodo, f.fecha_emision AS fechaEmision, f.cae, f.estado AS estadoCodigo,
                   f.punto_venta AS puntoVenta, f.fecha_vto_cae AS fechaVtoCae, f.moneda, f.observaciones,
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
    public long create(Map<String, Object> body)
            throws BadRequestException {

        requireEdit();

        FacturaValidada factura = validarFactura(body);

        KeyHolder kh = new GeneratedKeyHolder();

        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("cuit", factura.cuit())
            .addValue("razon", factura.razonSocial())
            .addValue("comprobante", factura.comprobante())
            .addValue("total", factura.total())
            .addValue("neto", factura.neto())
            .addValue("iva", factura.iva())
            .addValue("periodo", factura.periodo())
            .addValue("tipoComp", factura.tipoComprobanteId())
            .addValue("puntoVenta", factura.puntoVenta())
            .addValue("cae", factura.cae())
            .addValue("fechaVtoCae", factura.fechaVtoCae())
            .addValue("moneda", factura.moneda())
            .addValue("fechaEmision", factura.fechaEmision())
            .addValue("obs", factura.observaciones());

        repo.jdbc().update("""
            INSERT INTO factura (
                cuit_emisor,
                razon_social,
                numero_comprobante,
                importe_total,
                importe_neto,
                importe_iva,
                periodo_facturado,
                fecha_emision,
                tipo_comprobante_id,
                punto_venta,
                cae,
                fecha_vto_cae,
                moneda,
                estado,
                origen,
                observaciones
            )
            VALUES (
                :cuit,
                :razon,
                :comprobante,
                :total,
                :neto,
                :iva,
                :periodo,
                :fechaEmision,
                :tipoComp,
                :puntoVenta,
                :cae,
                :fechaVtoCae,
                :moneda,
                'SIN_ASIGNAR',
                'MANUAL',
                :obs
            )
            """,
            p,
            kh,
            new String[]{"id"}
        );

        long id = kh.getKey().longValue();

        boolean asignada = false;

        List<Long> locadorIds = repo.jdbc().query(
            """
            SELECT id
            FROM locador
            WHERE cuit = :cuit
            """,
            new MapSqlParameterSource()
                .addValue("cuit", factura.cuit()),
            (rs, rowNum) -> rs.getLong("id")
        );

        if (locadorIds.size() == 1) {

            long locadorId = locadorIds.get(0);

            List<Long> contratoIds = repo.jdbc().query(
                """
                SELECT id
                FROM contrato
                WHERE locador_id = :locadorId
                AND :periodo >= fecha_inicio
                AND :periodo <= fecha_vencimiento
                """,
                new MapSqlParameterSource()
                    .addValue("locadorId", locadorId)
                    .addValue("periodo", factura.periodo()),
                (rs, rowNum) -> rs.getLong("id")
            );


            if (contratoIds.size() == 1) {

                    long contratoId = contratoIds.get(0);

                    assign(id, contratoId);

                    asignada = true;
                
            }
        }

        audit.log(
            "factura",
            String.valueOf(id),
            "CREAR",
            "Creó una factura manual",
            "—",
            null,
            null
        );

        if (!asignada) {

            notificationService.create(
                Map.of(
                    "texto",
                    "La factura de comprobante '"
                        + factura.comprobante()
                        + "' no pudo ser asignada automáticamente."
                )
            );
        }

        return id;
    }



    @Transactional
    public void update(long id, Map<String, Object> body)
            throws BadRequestException {

        requireEdit();

        get(id);

        FacturaValidada factura = validarFactura(body);

        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("cuit", factura.cuit())
            .addValue("razon", factura.razonSocial())
            .addValue("comprobante", factura.comprobante())
            .addValue("total", factura.total())
            .addValue("neto", factura.neto())
            .addValue("iva", factura.iva())
            .addValue("periodo", factura.periodo())
            .addValue("fechaEmision", factura.fechaEmision())
            .addValue("obs", factura.observaciones());

        repo.jdbc().update("""
            UPDATE factura
            SET
                cuit_emisor = :cuit,
                razon_social = :razon,
                numero_comprobante = :comprobante,
                importe_total = :total,
                importe_neto = :neto,
                importe_iva = :iva,
                periodo_facturado = :periodo,
                fecha_emision = :fechaEmision,
                observaciones = :obs
            WHERE id = :id
            """,
            p
        );

        List<Long> locadorIds = repo.jdbc().query(
            """
            SELECT id
            FROM locador
            WHERE cuit = :cuit
            """,
            new MapSqlParameterSource()
                .addValue("cuit", factura.cuit()),
            (rs, rowNum) -> rs.getLong("id")
        );



        if (locadorIds.size() == 1) {

            long locadorId = locadorIds.get(0);

            List<Long> contratoIds = repo.jdbc().query(
                """
                SELECT id
                FROM contrato
                WHERE locador_id = :locadorId
                AND :periodo >= fecha_inicio
                AND :periodo <= fecha_vencimiento
                """,
                new MapSqlParameterSource()
                    .addValue("locadorId", locadorId)
                    .addValue("periodo", factura.periodo()),
                (rs, rowNum) -> rs.getLong("id")
            );

            if (contratoIds.size() == 1) {

                    long contratoId = contratoIds.get(0);

                    assign(id, contratoId);
            }
            
        }

        audit.log(
            "factura",
            String.valueOf(id),
            "EDITAR",
            "Editó la factura",
            "—",
            null,
            null
        );
    }

    @Transactional
    public void assign(long id, long contratoId)
            throws BadRequestException {

        requireEdit();

        // =========================================================
        // 1. OBTENER CONTRATO
        // =========================================================
        Map<String, Object> contrato = repo.queryOne(
            """
            SELECT
                c.id,
                c.inmueble_id AS inmuebleId,
                i.nis,
                i.denominacion AS denom
            FROM contrato c
            INNER JOIN inmueble i
                ON i.id = c.inmueble_id
            WHERE c.id = :contratoId
            """,
            new MapSqlParameterSource()
                .addValue("contratoId", contratoId)
        );

        if (contrato == null) {
            throw new NotFoundException("Contrato no encontrado");
        }

        String nis = (String) contrato.get("nis");
        String sucursal = (String) contrato.get("denom");

        // =========================================================
        // 2. OBTENER FACTURA
        // =========================================================
        Map<String, Object> factura = repo.queryOne(
            """
            SELECT
                id,
                contrato_id AS contratoIdActual,
                importe_total AS importeTotal,
                periodo_facturado AS periodoFacturado,
                numero_comprobante AS numeroComprobante
            FROM factura
            WHERE id = :facturaId
            """,
            new MapSqlParameterSource()
                .addValue("facturaId", id)
        );

        if (factura == null) {
            throw new NotFoundException("Factura no encontrada");
        }

        LocalDate periodo = factura.get("periodoFacturado") == null
            ? null
            : ((LocalDate) factura.get("periodoFacturado"));

        if (periodo == null) {
            throw new BadRequestException(
                "La factura no tiene un período facturado."
            );
        }

        String comprobante =
            (String) factura.get("numeroComprobante");

        // =========================================================
        // 3. BUSCAR LA ÚNICA CONCILIACIÓN DEL CONTRATO
        // =========================================================
        Map<String, Object> conciliacion = repo.queryOne(
            """
            SELECT
                id,
                importe_esperado AS importeEsperado
            FROM conciliacion
            WHERE contrato_id = :contratoId
            """,
            new MapSqlParameterSource()
                .addValue("contratoId", contratoId)
        );

        if (conciliacion == null) {
            throw new NotFoundException(
                "No existe una conciliación para el contrato "
                    + contratoId
                    + "."
            );
        }

        long conciliacionId =
            ((Number) conciliacion.get("id")).longValue();

        BigDecimal esperado =
            conciliacion.get("importeEsperado") == null
                ? BigDecimal.ZERO
                : (BigDecimal) conciliacion.get("importeEsperado");

        // =========================================================
        // 4. ELIMINAR RELACIONES ANTERIORES DE LA FACTURA
        // =========================================================
        MapSqlParameterSource relacionParams =
            new MapSqlParameterSource()
                .addValue("facturaId", id)
                .addValue("contratoId", contratoId)
                .addValue("conciliacionId", conciliacionId);

        repo.jdbc().update(
            """
            DELETE FROM conciliacion_factura
            WHERE factura_id = :facturaId
            """,
            relacionParams
        );

        // =========================================================
        // 5. ASIGNAR FACTURA AL CONTRATO
        // =========================================================
        int updated = repo.jdbc().update(
            """
            UPDATE factura
            SET
                contrato_id = :contratoId,
                inmueble_id = :inmuebleId,
                estado = N'PENDIENTE'
            WHERE id = :facturaId
            """,
            new MapSqlParameterSource()
                .addValue("facturaId", id)
                .addValue("contratoId", contratoId)
                .addValue(
                    "inmuebleId",
                    contrato.get("inmuebleId")
                )
        );

        if (updated == 0) {
            throw new NotFoundException(
                "No se pudo asignar la factura."
            );
        }

        // =========================================================
        // 6. CREAR conciliacion_factura
        // =========================================================
        repo.jdbc().update(
            """
            INSERT INTO conciliacion_factura (
                conciliacion_id,
                factura_id
            )
            SELECT
                :conciliacionId,
                :facturaId
            WHERE NOT EXISTS (
                SELECT 1
                FROM conciliacion_factura
                WHERE conciliacion_id = :conciliacionId
                AND factura_id = :facturaId
            )
            """,
            relacionParams
        );

        // =========================================================
        // 7. RECALCULAR LA CONCILIACIÓN COMPLETA
        // =========================================================
        Map<String, Object> resumen = repo.jdbc().queryForMap(
            """
            SELECT
                COUNT(*) AS cantidad,
                ISNULL(SUM(f.importe_total), 0) AS totalFacturado
            FROM conciliacion_factura cf
            INNER JOIN factura f
                ON f.id = cf.factura_id
            WHERE cf.conciliacion_id = :conciliacionId
            """,
            relacionParams
        );

        int cantidad =
            ((Number) resumen.get("cantidad")).intValue();

        BigDecimal nuevoFacturado =
            resumen.get("totalFacturado") == null
                ? BigDecimal.ZERO
                : (BigDecimal) resumen.get("totalFacturado");

        BigDecimal tolerancia =
            esperado
                .abs()
                .multiply(new BigDecimal("0.03"));

        BigDecimal desvio =
            nuevoFacturado
                .subtract(esperado)
                .abs();

        String estado;

        if (cantidad == 0) {
            estado = "SIN_FACTURA";
        } else if (nuevoFacturado.compareTo(esperado) == 0) {
            estado = "OK";
        } else if (desvio.compareTo(tolerancia) <= 0) {
            estado = "OK_CON_DIF";
        } else {
            estado = "CON_DIFERENCIA";
        }

        repo.jdbc().update(
            """
            UPDATE conciliacion
            SET
                importe_facturado = :importeFacturado,
                estado = :estado
            WHERE id = :conciliacionId
            """,
            new MapSqlParameterSource()
                .addValue("conciliacionId", conciliacionId)
                .addValue(
                    "importeFacturado",
                    nuevoFacturado
                )
                .addValue("estado", estado)
        );

        // =========================================================
        // 8. AUDITORÍA
        // =========================================================
        String ref =
            nis + " · " + sucursal;

        notificationService.create(
            Map.of(
                "texto",
                "La factura '"
                    + comprobante
                    + "' se asignó al contrato con NIS '"
                    + nis
                    + "' y sucursal '"
                    + sucursal
                    + "'."
            )
        );

        audit.log(
            "factura",
            String.valueOf(id),
            "ASIGNAR",
            "Asignó factura al contrato",
            ref,
            null,
            null
        );
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

    private String requireString(Map<String, Object> body, String key, String nombre_campo) throws BadRequestException {
        if (!body.containsKey(key)
                || body.get(key) == null
                || !(body.get(key) instanceof String value)
                || value.isBlank()) {

            throw new BadRequestException(
                "El campo '" + nombre_campo + "' es obligatorio y debe tener contenido."
            );
        }

        return value.trim();
    }

    private YearMonth asYearMonth(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return YearMonth.parse(value.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
}

private record FacturaValidada(
    String cuit,
    String nis,
    String razonSocial,
    String comprobante,
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

private FacturaValidada validarFactura(Map<String, Object> body)
        throws BadRequestException {

    String cuit = requireString(body, "cuit", "CUIT");
    cuit = cuit.replace("-", "").trim();

    if (cuit.length() < 11) {
        throw new BadRequestException(
            "El CUIT debe tener al menos 11 caracteres."
        );
    }

    String razonSocial = requireString(body, "razonSocial", "Razón Social");

    String comprobante = requireString(body, "comprobante", "Comprobante");

    String nis = (String) body.get("nis");

    String observaciones = (String) body.get("observaciones");

    BigDecimal total = asDecimal(body.get("importe"));

    if (total == null) {
        throw new BadRequestException(
            "El importe debe ser un número válido."
        );
    }

    if (total.compareTo(BigDecimal.ZERO) <= 0) {
        throw new BadRequestException(
            "El importe debe ser mayor a 0."
        );
    }

    YearMonth periodo = asYearMonth(body.get("periodo"));

    if (periodo == null) {
        throw new BadRequestException(
            "El período no es válido."
        );
    }

    LocalDate periodoFecha = periodo.atDay(1);

    LocalDate hoy = LocalDate.now();

    LocalDate fechaEmision = asDate(body.get("fechaEmision"));

    if (fechaEmision == null) {
        throw new BadRequestException(
            "La fecha de emisión no es válida."
        );
    }

    YearMonth mesActual = YearMonth.from(hoy);

    if (periodo.isAfter(mesActual)) {
        throw new BadRequestException(
            "El período no puede ser mayor al mes actual."
        );
    }

    if (fechaEmision.isAfter(hoy)) {
        throw new BadRequestException(
            "La fecha de emisión no puede ser mayor a la fecha actual."
        );
    }

    BigDecimal neto = total.divide(
        new BigDecimal("1.21"),
        2,
        RoundingMode.HALF_UP
    );

    BigDecimal iva = total.subtract(neto);

    return new FacturaValidada(
        cuit,
        nis,
        razonSocial,
        comprobante,
        observaciones,
        total,
        neto,
        iva,
        periodoFecha,
        fechaEmision,
        puntoVenta(body.get("puntoVenta")),
        tipoComprobanteId(str(body.get("tipo"))),
        textoOpcional(body.get("cae")),
        fechaOpcional(body.get("fechaVencimientoCae"), "fecha de vencimiento del CAE"),
        moneda(body.get("moneda"))
    );
}

private Integer tipoComprobanteId(String tipo) throws BadRequestException {
    if (tipo == null || tipo.isBlank()) {
        return 1;
    }
    String codigo = switch (tipo.trim().toUpperCase()) {
        case "A", "FA" -> "FA";
        case "B", "FB" -> "FB";
        case "C", "FC" -> "FC";
        default -> throw new BadRequestException(
            "El tipo de comprobante '" + tipo + "' no es válido. Use A o C."
        );
    };
    List<Integer> ids = repo.jdbc().query(
        "SELECT id FROM tipo_comprobante WHERE codigo = :codigo",
        new MapSqlParameterSource("codigo", codigo),
        (rs, rowNum) -> rs.getInt("id")
    );
    if (ids.isEmpty()) {
        throw new BadRequestException("No existe el tipo de comprobante " + codigo + ".");
    }
    return ids.get(0);
}

private static Integer puntoVenta(Object raw) throws BadRequestException {
    if (raw == null) {
        return null;
    }
    String s = raw.toString().trim();
    if (s.isEmpty()) {
        return null;
    }
    try {
        return Integer.parseInt(s);
    } catch (NumberFormatException e) {
        throw new BadRequestException("El punto de venta no es válido.");
    }
}

private static String textoOpcional(Object raw) {
    if (raw == null) {
        return null;
    }
    String s = raw.toString().trim();
    return s.isEmpty() ? null : s;
}

private static LocalDate fechaOpcional(Object raw, String nombre) throws BadRequestException {
    if (raw == null) {
        return null;
    }
    String s = raw.toString().trim();
    if (s.isEmpty()) {
        return null;
    }
    LocalDate fecha = asDate(raw);
    if (fecha == null) {
        throw new BadRequestException("La " + nombre + " no es válida.");
    }
    return fecha;
}

private static String moneda(Object raw) throws BadRequestException {
    if (raw == null) {
        return null;
    }
    String s = raw.toString().trim().toUpperCase();
    if (s.isEmpty()) {
        return null;
    }
    if (s.length() != 3) {
        throw new BadRequestException("La moneda debe tener 3 caracteres.");
    }
    return s;
}
}
