package com.correoargentino.sga.utils;


public final class InvoiceSql {
    private InvoiceSql() {}

    public static final String PLANIFICADAS = """
        SELECT
            id,
            contrato_id,
            locador_id,
            porcentaje_esperado,
            monto_esperado,
            estado
        FROM factura_planificada
        """;

    public static final String SIN_ASIGNAR = """
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
        """;

    
    static String sugerencias(String top, String filtroCuit) {
        return """
            SELECT %s
                i.nis,
                i.denominacion AS sucursal,
                i.responsable AS responsable,
                RTRIM(lo.cuit) AS locadorCuit,
                lo.razon_social AS locadorRazon,
                lo.id AS locadorId,
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
            JOIN contrato_locador cl
                ON cl.contrato_id = c.id
            JOIN locador lo
                ON lo.id = cl.locador_id
            JOIN estado_contrato e
                ON e.id = c.estado_contrato_id
            WHERE %s e.codigo <> 'RESCINDIDO'
            ORDER BY i.nis, lo.razon_social
            """.formatted(top, filtroCuit);
    }

    public static final String SUGERENCIAS_POR_CUIT = sugerencias("TOP 8", "lo.cuit = :cuit AND");
    public static final String SUGERENCIAS_TODAS    = sugerencias("", "");

    public static final String DETALLE = """
        SELECT f.id, f.cuit_emisor AS cuit, f.razon_social AS razonSocial, f.numero_comprobante AS comprobante,
               f.importe_total AS importe, f.importe_neto AS neto, f.importe_iva AS iva,
               f.periodo_facturado AS periodo, f.fecha_emision AS fechaEmision, f.cae, f.estado AS estadoCodigo,
               f.punto_venta AS puntoVenta, f.fecha_vto_cae AS fechaVtoCae, f.moneda, f.observaciones, f.tipo_factura,
               tc.nombre AS tipoComprobante, tc.id AS tipoComprobanteId,
               c.id AS contratoId, i.nis AS contratoNis, i.denominacion AS contratoDenom
          FROM factura f
          LEFT JOIN tipo_comprobante tc ON tc.id=f.tipo_comprobante_id
          LEFT JOIN contrato c ON c.id=f.contrato_id
          LEFT JOIN inmueble i ON i.id=c.inmueble_id
         WHERE f.id=:id
        """;

    public static final String INSERT = """
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
            observaciones,
            tipo_factura
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
            :obs,
            :tipo_factura
        )
        """;

    public static final String UPDATE = """
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
            observaciones = :obs,
            tipo_factura = :tipo_factura
        WHERE id = :id
        """;

    public static final String LOCADOR_POR_CUIT = """
        SELECT id
        FROM locador
        WHERE cuit = :cuit
        """;

    public static final String CONTRATO_VIGENTE_DEL_LOCADOR = """
    SELECT c.id
      FROM contrato c
      JOIN contrato_locador cl ON cl.contrato_id = c.id
     WHERE cl.locador_id = :locadorId
    """;

    /** Locadores del contrato con su indicador de impuestos. */
    public static final String LOCADORES_DEL_CONTRATO = """
        SELECT lo.id,
               lo.indicador_impuesto AS indicadorImpuesto
          FROM contrato_locador cl
          JOIN locador lo ON lo.id = cl.locador_id
         WHERE cl.contrato_id = :contratoId
        """;

    public static final String CONTRATO_CON_INMUEBLE = """
        SELECT
            c.id,
            c.inmueble_id AS inmuebleId,
            i.nis,
            i.denominacion AS denom
        FROM contrato c
        INNER JOIN inmueble i
            ON i.id = c.inmueble_id
        WHERE c.id = :contratoId
        """;

    public static final String FACTURA_PARA_ASIGNAR = """
        SELECT
            id,
            contrato_id AS contratoIdActual,
            importe_total AS importeTotal,
            periodo_facturado AS periodoFacturado,
            numero_comprobante AS numeroComprobante
        FROM factura
        WHERE id = :facturaId
        """;

    public static final String CONCILIACION_DEL_CONTRATO = """
        SELECT
            id,
            importe_esperado AS importeEsperado
        FROM conciliacion
        WHERE contrato_id = :contratoId
        """;

    public static final String BORRAR_RELACIONES = """
        DELETE FROM conciliacion_factura
        WHERE factura_id = :facturaId
        """;

    public static final String ASIGNAR_CONTRATO = """
        UPDATE factura
        SET
            contrato_id = :contratoId,
            inmueble_id = :inmuebleId,
            estado = N'PENDIENTE'
        WHERE id = :facturaId
        """;

    public static final String CREAR_RELACION = """
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
        """;

    public static final String RESUMEN_CONCILIACION = """
        SELECT
            COUNT(*) AS cantidad,
            ISNULL(SUM(f.importe_total), 0) AS totalFacturado
        FROM conciliacion_factura cf
        INNER JOIN factura f
            ON f.id = cf.factura_id
        WHERE cf.conciliacion_id = :conciliacionId
        """;

    public static final String ACTUALIZAR_CONCILIACION = """
        UPDATE conciliacion
        SET
            importe_facturado = :importeFacturado,
            estado = :estado
        WHERE id = :conciliacionId
        """;

    public static final String TIPO_COMPROBANTE_POR_CODIGO =
        "SELECT id FROM tipo_comprobante WHERE codigo = :codigo";

    public static final String TIPO_Y_CONTRATO_DE_FACTURA = """
        SELECT f.tipo_factura AS tipoFactura,
            f.contrato_id  AS contratoId
        FROM factura f
        WHERE f.id = :facturaId
        """;

    public static final String DESASIGNAR_FACTURA = """
        UPDATE factura
        SET contrato_id = NULL,
            inmueble_id = NULL,
            estado      = 'SIN_ASIGNAR'
        WHERE id = :facturaId
        """;

    public static final String BORRAR_RELACIONES_DE_FACTURA = """
        DELETE FROM conciliacion_factura
        WHERE factura_id = :facturaId
        """;
}