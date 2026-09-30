package com.correoargentino.sga.utils;


/** Sentencias SQL de contratos. Solo constantes, sin lógica. */
public final class ContractSql {

    private ContractSql() {}

    // =====================================================
    // contrato
    // =====================================================

    public static final String ULTIMO_NUMERO =
        "SELECT ISNULL(MAX(TRY_CAST(REPLACE(numero,'C-','') AS INT)),1000) FROM contrato";

    public static final String INSERT_CONTRATO = """
        INSERT INTO contrato (numero, inmueble_id, locador_id, acreedor_sap_id, tipo_contrato_id, estado_contrato_id,
                              fecha_inicio, fecha_vencimiento, moneda, importe_inicial, deposito_garantia,
                              indice_ajuste_id, periodicidad_ajuste, tipo_comprobante_id, tolerancia_importe_pct,
                              tipo_facturacion, cantidad_facturas,
                              ceco_sap, division_sap, cuenta_gasto, indicador_impuesto)
        VALUES (:numero, :inmuebleId, :locadorId, :acreedorId, :tipoContrato, :estadoId,
                :inicio, :venc, 'ARS', :importe, :deposito,
                :indiceId, :periodicidad, :tipoComp, :tolerancia,
                :tipoFacturacion, :cantidadFacturas,
                :cecoSap, :divisionSap, :cuentaGasto, :indicadorImpuesto)
        """;

    public static final String UPDATE_CONTRATO = """
        UPDATE contrato
        SET
            inmueble_id = :inmuebleId,
            locador_id = :locadorId,
            acreedor_sap_id = :acreedorId,
            tipo_contrato_id = :tipoContrato,
            estado_contrato_id = :estadoId,
            fecha_inicio = :inicio,
            fecha_vencimiento = :venc,
            importe_inicial = :importe,
            deposito_garantia = :deposito,
            indice_ajuste_id = :indiceId,
            periodicidad_ajuste = :periodicidad,
            tipo_comprobante_id = :tipoComp,
            tolerancia_importe_pct = :tolerancia,
            tipo_facturacion = :tipoFacturacion,
            cantidad_facturas = :cantidadFacturas,
            ceco_sap = :cecoSap,
            division_sap = :divisionSap,
            cuenta_gasto = :cuentaGasto,
            indicador_impuesto = :indicadorImpuesto
        WHERE id = :id
        """;

    public static final String RESCINDIR =
        "UPDATE contrato SET estado_contrato_id=4 WHERE id=:id";

    // =====================================================
    // factura_planificada
    // =====================================================

    public static final String DELETE_PLANIFICADAS = """
        DELETE FROM factura_planificada
        WHERE contrato_id = :id
        """;

    public static final String INSERT_PLANIFICADA = """
        INSERT INTO factura_planificada (contrato_id, porcentaje_esperado, monto_esperado, estado)
        VALUES (:contratoId, :porcentaje, :monto, 'PENDIENTE')
        """;

    // =====================================================
    // contrato_valor
    // =====================================================

    public static final String IMPORTE_VIGENTE = """
        SELECT importe_mensual
        FROM contrato_valor
        WHERE contrato_id = :id
        AND vigencia_hasta IS NULL
        """;

    public static final String VALOR_VIGENTE = """
        SELECT TOP 1 id, vigencia_desde AS desde, importe_mensual AS importe
          FROM contrato_valor
         WHERE contrato_id = :id AND vigencia_hasta IS NULL
         ORDER BY vigencia_desde DESC
        """;

    public static final String VALORES_EN_FECHA = """
        SELECT COUNT(*)
          FROM contrato_valor
         WHERE contrato_id = :id AND vigencia_desde = :desde
        """;

    public static final String INSERT_VALOR = """
        INSERT INTO contrato_valor
            (contrato_id, vigencia_desde, vigencia_hasta, importe_mensual, origen, indice_id, coeficiente_aplicado)
        VALUES
            (:contratoId, :desde, NULL, :importe, :origen, :indiceId, :coeficiente)
        """;

    public static final String CERRAR_VALOR = """
        UPDATE contrato_valor
           SET vigencia_hasta = :hasta
         WHERE id = :valorId
        """;

    /** Usa la fecha del servidor de base (GETDATE), igual que el código original. */
    public static final String CERRAR_VALOR_HOY = """
        UPDATE contrato_valor
        SET vigencia_hasta = CAST(GETDATE() AS DATE)
        WHERE contrato_id = :id
        AND vigencia_hasta IS NULL
        """;

    /** Usa la fecha del servidor de base (GETDATE), igual que el código original. */
    public static final String INSERT_VALOR_ACUERDO_HOY = """
        INSERT INTO contrato_valor (contrato_id, vigencia_desde, vigencia_hasta, importe_mensual, origen)
        VALUES (:id, CAST(GETDATE() AS DATE), NULL, :importe, 'ACUERDO')
        """;

    // =====================================================
    // inmueble
    // =====================================================

    public static final String INSERT_INMUEBLE = """
        INSERT INTO inmueble (nis, denominacion, direccion, localidad_id, region_id, superficie_cubierta_m2, activo)
        VALUES (:nis, :denom, :direccion, :localidadId, :regionId, :sup, 1)
        """;

    public static final String INSERT_INMUEBLE_DESTINO =
        "INSERT INTO inmueble_destino (inmueble_id, destino_id) VALUES (:i, :d)";

    // =====================================================
    // locador / acreedor SAP
    // =====================================================

    public static final String LOCADORES_POR_CUIT = """
        SELECT COUNT(*)
        FROM locador
        WHERE cuit = :cuit
        """;

    public static final String INSERT_LOCADOR = """
        INSERT INTO locador (tipo_persona, razon_social, cuit, email, telefono, activo)
        VALUES ('JURIDICA', :razonSocial, :cuit, :email, :telefono, 1)
        """;

    public static final String ACREEDOR_POR_CODIGO =
        "SELECT id FROM acreedor_sap WHERE codigo_sap = :codigo";

    public static final String INSERT_ACREEDOR = """
        INSERT INTO acreedor_sap (codigo_sap, locador_id, descripcion)
        VALUES (:codigo, :locadorId, :codigo)
        """;

    // =====================================================
    // indice_ajuste
    // =====================================================

    public static final String INDICES_POR_CODIGO = """
        SELECT COUNT(*)
        FROM indice_ajuste
        WHERE codigo = :codigo
        """;

    public static final String INDICES_POR_ID =
        "SELECT COUNT(*) FROM indice_ajuste WHERE id = :id";
}