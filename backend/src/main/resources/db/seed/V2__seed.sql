/* =========================================================================
   SGA Alquileres - Datos de ejemplo (reales, servidos por la API)
   ========================================================================= */

SET NOCOUNT ON;

/* ---------- Regiones ---------- */
INSERT INTO region (id, nombre, activo) VALUES
 (1, N'Metropolitana', 1),
 (2, N'Norte', 1),
 (3, N'Sur', 1),
 (4, N'Cuyo', 1),
 (5, N'NEA', 1);

/* ---------- Provincias ---------- */
INSERT INTO provincia (id, nombre, codigo_indec) VALUES
 (1, N'Ciudad Autónoma de Buenos Aires', '02'),
 (2, N'Buenos Aires', '06'),
 (3, N'Córdoba', '14'),
 (4, N'Santa Fe', '82'),
 (5, N'Mendoza', '50'),
 (6, N'Salta', '66'),
 (7, N'Chubut', '26'),
 (8, N'Misiones', '54');

/* ---------- Localidades (IDENTITY => ids 1..10) ---------- */
INSERT INTO localidad (provincia_id, nombre, codigo_postal) VALUES
 (1, N'CABA - Centro', '1001'),
 (2, N'La Plata', '1900'),
 (2, N'Mar del Plata', '7600'),
 (3, N'Córdoba Capital', '5000'),
 (4, N'Rosario', '2000'),
 (5, N'Mendoza Capital', '5500'),
 (6, N'Salta Capital', '4400'),
 (7, N'Comodoro Rivadavia', '9000'),
 (8, N'Posadas', '3300'),
 (2, N'Bahía Blanca', '8000');

/* ---------- Destinos de uso ---------- */
INSERT INTO destino_uso (id, codigo, nombre) VALUES
 (1, N'SUC', N'Sucursal'),
 (2, N'CDD', N'Centro de Distribución'),
 (3, N'ADM', N'Administrativo');

/* ---------- Titulo acreditante ---------- */
INSERT INTO titulo_acreditante (id, codigo, descripcion) VALUES
 (1, N'ESCRITURA', N'Escritura traslativa de dominio'),
 (2, N'BOLETO', N'Boleto de compraventa'),
 (3, N'CESION', N'Cesión de derechos');

/* ---------- Centros de costo ---------- */
INSERT INTO centro_costo (id, codigo, descripcion) VALUES
 (1, N'CC-1001', N'Operaciones Metropolitana'),
 (2, N'CC-1002', N'Operaciones Norte'),
 (3, N'CC-1003', N'Operaciones Sur'),
 (4, N'CC-1004', N'Operaciones Cuyo'),
 (5, N'CC-1005', N'Operaciones NEA');

/* ---------- Tipos de contrato ---------- */
INSERT INTO tipo_contrato (id, codigo, nombre) VALUES
 (1, N'LOC_COM', N'Locación comercial'),
 (2, N'LOC_OPC', N'Locación con opción a compra');

/* ---------- Estados de contrato ---------- */
INSERT INTO estado_contrato (id, codigo, nombre, es_final) VALUES
 (1, N'VIGENTE', N'Vigente', 0),
 (2, N'PROX_VENCER', N'Próximo a vencer', 0),
 (3, N'VENCIDO', N'Vencido', 1),
 (4, N'RESCINDIDO', N'Rescindido', 1);

/* ---------- Tipos de comprobante ---------- */
INSERT INTO tipo_comprobante (id, codigo, nombre, codigo_afip) VALUES
 (1, N'FA', N'Factura A', N'001'),
 (2, N'FB', N'Factura B', N'006'),
 (3, N'FC', N'Factura C', N'011');

/* ---------- Indices de ajuste ---------- */
INSERT INTO indice_ajuste (id, codigo, nombre, fuente) VALUES
 (1, N'IPC', N'Índice de Precios al Consumidor', N'INDEC'),
 (2, N'ICL', N'Índice de Contratos de Locación', N'BCRA');

/* ---------- Valores de indices ---------- */
INSERT INTO indice_valor (indice_id, periodo, valor) VALUES
 (1, '2025-01-01', 1.000),(1, '2025-04-01', 1.047),(1, '2025-07-01', 1.061),(1, '2025-10-01', 1.061),(1, '2026-01-01', 1.082),(1, '2026-04-01', 1.094),
 (2, '2025-01-01', 1.000),(2, '2025-04-01', 1.038),(2, '2025-07-01', 1.052),(2, '2025-10-01', 1.070),(2, '2026-01-01', 1.091),(2, '2026-04-01', 1.103);

/* ---------- Usuarios (proyección; sin credenciales locales) ---------- */
INSERT INTO usuario (username, nombre, email, activo) VALUES
 (N'jmartinez', N'Juan Martínez', N'juan.martinez@correoargentino.com.ar', 1),
 (N'csuarez',   N'Carla Suárez',  N'carla.suarez@correoargentino.com.ar', 1),
 (N'mgomez',    N'Marcelo Gómez', N'marcelo.gomez@correoargentino.com.ar', 1),
 (N'lauditor',  N'Laura Auditora',N'laura.auditora@correoargentino.com.ar', 1),
 (N'rpa',       N'RPA Sistema',   N'rpa@correoargentino.com.ar', 1);

INSERT INTO usuario_rol_cache (usuario_id, rol) VALUES
 (1, N'ANALISTA'), (2, N'SUPERVISOR'), (3, N'ANALISTA'), (4, N'AUDITOR'), (5, N'SISTEMA');

/* ---------- Locadores ---------- */
INSERT INTO locador (tipo_persona, razon_social, cuit, email, telefono, cbu, activo) VALUES
 (N'JURIDICA', N'Propietaria del Plata SRL',   '30712345672', N'contacto@delplata.com',   N'011 4555-2310', N'2850590940090418135201', 1),
 (N'FISICA',   N'Rodríguez, Ana María',        '27301234564', N'ana.rodriguez@mail.com',  N'011 4444-1122', N'0170099220000067797370', 1),
 (N'JURIDICA', N'Inversiones del Sur SA',      '30685412309', N'admin@invsur.com.ar',     N'0291 456-7788', N'0110599520000012345678', 1),
 (N'FISICA',   N'Pérez, Carlos Alberto',       '20259876541', N'carlos.perez@mail.com',   N'0351 422-3344', N'0720099988000098765432', 1),
 (N'JURIDICA', N'Fideicomiso Norte',           '30687412309', N'fideicomiso@norte.com',   N'0387 421-9900', N'0140099803200055667788', 1),
 (N'JURIDICA', N'Grupo Inmobiliario Cuyo SA',  '33710293845', N'info@grupocuyo.com',      N'0261 429-1010', N'0290099911000011223344', 1),
 (N'FISICA',   N'Gómez, Marta Susana',         '27284561238', N'marta.gomez@mail.com',    N'0341 455-6677', N'0850099944000099887766', 1),
 (N'JURIDICA', N'Litoral Propiedades SRL',     '30709182734', N'ventas@litoralprop.com',  N'0376 443-2211', N'0110099922000033445566', 1),
 (N'JURIDICA', N'razon prueba 1',  '11111111111', N'info@grupocuyo.com',      N'0261 429-1010', N'0290099911000011223344', 1),
 (N'FISICA',   N'razon prueba 2',         '22222222222', N'marta.gomez@mail.com',    N'0341 455-6677', N'0850099944000099887766', 1),
 (N'JURIDICA', N'razon prueba 3',     '33333333333', N'ventas@litoralprop.com',  N'0376 443-2211', N'0110099922000033445566', 1);


INSERT INTO acreedor_sap (codigo_sap, locador_id, descripcion) VALUES
 (N'AC-004821', 1, N'Propietaria del Plata SRL'),
 (N'AC-004822', 2, N'Rodríguez Ana María'),
 (N'AC-004823', 3, N'Inversiones del Sur SA'),
 (N'AC-004824', 4, N'Pérez Carlos Alberto'),
 (N'AC-004825', 5, N'Fideicomiso Norte'),
 (N'AC-004826', 6, N'Grupo Inmobiliario Cuyo SA'),
 (N'AC-004827', 7, N'Gómez Marta Susana'),
 (N'AC-004828', 8, N'Litoral Propiedades SRL'),
 (N'AC-004829', 9, N'Acreedor prueba 1'),
 (N'AC-004830', 10, N'Acreedor prueba 2'),
 (N'AC-004831', 11, N'Acreedor prueba 3');

/* =========================================================================
   1) INMUEBLES + CONTRATOS + VALORES + FACTURAS PLANIFICADAS + SEGUROS
      (40 contratos facturables)
   ========================================================================= */
DECLARE @i       INT  = 0;
DECLARE @N       INT  = 40;
DECLARE @refDate DATE = '2026-07-23';

IF OBJECT_ID('tempdb..#contratos_seed') IS NOT NULL
    DROP TABLE #contratos_seed;

CREATE TABLE #contratos_seed (
    contrato_id   BIGINT PRIMARY KEY,
    orden         INT,
    cant_facturas INT
);

DECLARE @nis          NVARCHAR(40);
DECLARE @regionId     SMALLINT;
DECLARE @locId        INT;
DECLARE @ccId         INT;
DECLARE @locadorId    BIGINT;
DECLARE @acreedorId   BIGINT;
DECLARE @tipoContrato SMALLINT;
DECLARE @destino      SMALLINT;
DECLARE @indice       SMALLINT;
DECLARE @estadoId     SMALLINT;
DECLARE @venc         DATE;
DECLARE @inicio       DATE;
DECLARE @supCubierta  DECIMAL(12,2);
DECLARE @importe      DECIMAL(18,2);
DECLARE @deposito     DECIMAL(18,2);
DECLARE @inmId        BIGINT;
DECLARE @conId        BIGINT;
DECLARE @cantPlan     INT;

/* Variables para generar las facturas planificadas */
DECLARE @planK        INT;
DECLARE @planPct      INT;
DECLARE @planAcum     DECIMAL(18,2);
DECLARE @planMonto    DECIMAL(18,2);

WHILE @i < @N
BEGIN
    SET @nis          = N'B0' + CAST(501 + @i AS NVARCHAR(10));
    SET @regionId     = (@i % 5) + 1;
    SET @locId        = (@i % 10) + 1;
    SET @ccId         = @regionId;
    SET @locadorId    = (@i % 8) + 1;
    SET @acreedorId   = @locadorId;
    SET @tipoContrato = (@i % 2) + 1;
    SET @destino      = CASE WHEN @i % 4 = 0 THEN 2 ELSE 1 END;
    SET @indice       = (@i % 2) + 1;

    -- facturas planificadas del contrato POR PERIODO: 1, 2 o 3
    SET @cantPlan = (@i % 3) + 1;

    SET @estadoId = CASE
        WHEN @i % 5 = 0 THEN 3
        WHEN @i % 3 = 0 THEN 2
        ELSE 1
    END;

    SET @venc = CASE
        WHEN @estadoId = 3 THEN DATEADD(DAY, -(30 + @i), @refDate)
        WHEN @estadoId = 2 THEN DATEADD(DAY, (15 + (@i % 60)), @refDate)
        ELSE DATEADD(YEAR, 2, @refDate)
    END;

    SET @inicio = DATEADD(YEAR, -3, @venc);

    SET @supCubierta = 60 + @i * 12;
    SET @importe     = 320000 + @i * 41000;
    SET @deposito    = @importe * 2;

    INSERT INTO inmueble (
        nis, denominacion, direccion, localidad_id, region_id, centro_costo_id,
        titulo_acreditante_id, superficie_cubierta_m2, superficie_terreno_m2,
        responsable, responsable_email, responsable_telefono, activo
    )
    VALUES (
        @nis,
        CASE WHEN @destino = 2 THEN N'CDD ' ELSE N'Sucursal ' END
            + CHAR(65 + (@i % 26)),
        N'Av. Ejemplo ' + CAST(1000 + @i * 7 AS NVARCHAR(10)),
        @locId, @regionId, @ccId, (@i % 3) + 1,
        @supCubierta, @supCubierta * 1.6,
        N'María Fernández',
        N'maria.fernandez@correoargentino.com.ar',
        N'011 4315-0800',
        1
    );

    SET @inmId = CONVERT(BIGINT, SCOPE_IDENTITY());

    INSERT INTO inmueble_destino (inmueble_id, destino_id)
    VALUES (@inmId, @destino);

    IF @i % 4 = 0
        INSERT INTO inmueble_destino (inmueble_id, destino_id)
        VALUES (@inmId, 1);

    INSERT INTO contrato (
        numero, inmueble_id, locador_id, acreedor_sap_id, tipo_contrato_id,
        estado_contrato_id, fecha_inicio, fecha_vencimiento, moneda,
        importe_inicial, deposito_garantia, indice_ajuste_id, periodicidad_ajuste,
        tipo_comprobante_id, tolerancia_importe_pct, tipo_facturacion,
        observaciones, cantidad_facturas
    )
    VALUES (
        N'C-' + RIGHT('000000' + CAST(1000 + @i AS NVARCHAR(10)), 6),
        @inmId, @locadorId, @acreedorId, @tipoContrato, @estadoId,
        @inicio, @venc, N'ARS', @importe * 0.78, @deposito,
        @indice, N'TRIMESTRAL',
        CASE WHEN @tipoContrato = 1 THEN 1 ELSE 2 END,
        3.0,
        N'personalizado',
        N'Contrato generado para la demo del MVP.',
        @cantPlan
    );

    SET @conId = CONVERT(BIGINT, SCOPE_IDENTITY());

    INSERT INTO #contratos_seed (contrato_id, orden, cant_facturas)
    VALUES (@conId, @i, @cantPlan);

    INSERT INTO contrato_valor (
        contrato_id, vigencia_desde, vigencia_hasta, importe_mensual,
        origen, indice_id, coeficiente_aplicado
    )
    VALUES
      (@conId, '2025-01-01', '2025-03-31', @importe * 0.78, N'CONTRATO',       NULL,    NULL),
      (@conId, '2025-04-01', '2025-06-30', @importe * 0.83, N'AJUSTE_INDICE', @indice, 1.047),
      (@conId, '2025-07-01', '2025-09-30', @importe * 0.88, N'ACUERDO',        NULL,    NULL),
      (@conId, '2025-10-01', '2025-12-31', @importe * 0.93, N'AJUSTE_INDICE', @indice, 1.061),
      (@conId, '2026-01-01', NULL,         @importe,        N'AJUSTE_INDICE', @indice, 1.082);

    /* =====================================================================
       FACTURAS PLANIFICADAS DEL CONTRATO

       1 -> 100
       2 -> 50, 50
       3 -> 34, 33, 33

       La última factura absorbe el residuo de redondeo para que la suma
       de monto_esperado coincida exactamente con el importe mensual.
       ===================================================================== */
    SET @planK    = 1;
    SET @planAcum = 0;

    WHILE @planK <= @cantPlan
    BEGIN
        SET @planPct = CASE
            WHEN @planK = 1
                THEN 100 - (100 / @cantPlan) * (@cantPlan - 1)
            ELSE 100 / @cantPlan
        END;

        SET @planMonto = CASE
            WHEN @planK = @cantPlan
                THEN @importe - @planAcum
            ELSE ROUND(@importe * @planPct / 100.0, 2)
        END;

        INSERT INTO factura_planificada (
            contrato_id, porcentaje_esperado, monto_esperado, estado
        )
        VALUES (
            @conId, @planPct, @planMonto, N'PENDIENTE'
        );

        SET @planAcum = @planAcum + @planMonto;
        SET @planK    = @planK + 1;
    END;

    INSERT INTO contrato_seguro (
        contrato_id, tipo, nro_poliza, suma_asegurada, vigencia_hasta
    )
    VALUES (
        @conId, N'CAUCION',
        N'8' + CAST(8000 + @i AS NVARCHAR(10)),
        @importe * 3, '2026-12-31'
    );

    SET @i = @i + 1;
END

/* =========================================================================
   2) CONTRATOS B0601 / B0602 / B0603  -  SIN FACTURAS REALES
      (esperan 1 factura planificada del 100%)
   ========================================================================= */
DECLARE @j INT = 0;

DECLARE @nisExtra         NVARCHAR(40);
DECLARE @regionIdExtra    SMALLINT;
DECLARE @locIdExtra       INT;
DECLARE @ccIdExtra        INT;
DECLARE @destinoExtra     SMALLINT;
DECLARE @supCubiertaExtra DECIMAL(12,2);
DECLARE @inmIdExtra       BIGINT;
DECLARE @locadorIdExtra   BIGINT;
DECLARE @importeExtra     DECIMAL(18,2);
DECLARE @acreedorIdExtra  BIGINT;
DECLARE @conIdExtra       BIGINT;

WHILE @j < 3
BEGIN
    SET @nisExtra         = N'B0' + CAST(601 + @j AS NVARCHAR(10));
    SET @regionIdExtra    = (@j % 5) + 1;
    SET @locIdExtra       = (@j % 10) + 1;
    SET @ccIdExtra        = @regionIdExtra;
    SET @destinoExtra     = CASE WHEN @j = 0 THEN 2 ELSE 1 END;
    SET @supCubiertaExtra = 540 + @j * 12;

    INSERT INTO inmueble (
        nis, denominacion, direccion, localidad_id, region_id, centro_costo_id,
        titulo_acreditante_id, superficie_cubierta_m2, superficie_terreno_m2,
        responsable, responsable_email, responsable_telefono, activo
    )
    VALUES (
        @nisExtra,
        CASE WHEN @destinoExtra = 2 THEN N'CDD ' ELSE N'Sucursal ' END
            + CHAR(65 + @j),
        N'Av. Ejemplo ' + CAST(1300 + @j * 7 AS NVARCHAR(10)),
        @locIdExtra, @regionIdExtra, @ccIdExtra, (@j % 3) + 1,
        @supCubiertaExtra, @supCubiertaExtra * 1.6,
        N'María Fernández',
        N'maria.fernandez@correoargentino.com.ar',
        N'011 4315-0800',
        1
    );

    SET @inmIdExtra = CONVERT(BIGINT, SCOPE_IDENTITY());

    INSERT INTO inmueble_destino (inmueble_id, destino_id)
    VALUES (@inmIdExtra, @destinoExtra);

    IF @j = 0
    BEGIN
        SET @locadorIdExtra = 9;
        SET @importeExtra   = 289500.00;
    END
    ELSE IF @j = 1
    BEGIN
        SET @locadorIdExtra = 10;
        SET @importeExtra   = 361000.00;
    END
    ELSE
    BEGIN
        SET @locadorIdExtra = 11;
        SET @importeExtra   = 300000.00;
    END;

    SET @acreedorIdExtra = @locadorIdExtra;

    INSERT INTO contrato (
        numero, inmueble_id, locador_id, acreedor_sap_id, tipo_contrato_id,
        estado_contrato_id, fecha_inicio, fecha_vencimiento, moneda,
        importe_inicial, deposito_garantia, indice_ajuste_id, periodicidad_ajuste,
        tipo_comprobante_id, tolerancia_importe_pct, tipo_facturacion,
        observaciones, cantidad_facturas
    )
    VALUES (
        N'C-' + @nisExtra, @inmIdExtra, @locadorIdExtra, @acreedorIdExtra, 1, 1,
        '2023-07-01', '2028-07-01', N'ARS', @importeExtra, @importeExtra * 2,
        1, N'TRIMESTRAL', 1, 3.0, N'personalizado',
        N'Contrato sin facturas asociadas (bandeja).', 1
    );

    SET @conIdExtra = CONVERT(BIGINT, SCOPE_IDENTITY());

    INSERT INTO contrato_valor (
        contrato_id, vigencia_desde, vigencia_hasta, importe_mensual,
        origen, indice_id, coeficiente_aplicado
    )
    VALUES (
        @conIdExtra, '2026-01-01', NULL, @importeExtra,
        N'AJUSTE_INDICE', 1, 1.082
    );

    /* Factura planificada única del 100% */
    INSERT INTO factura_planificada (
        contrato_id, porcentaje_esperado, monto_esperado, estado
    )
    VALUES (
        @conIdExtra, 100, @importeExtra, N'PENDIENTE'
    );

    SET @j = @j + 1;
END;

/* =========================================================================
   3) CORRIDAS RPA
   ========================================================================= */
DECLARE @rpa TABLE (id BIGINT, periodo_desde DATE, estado NVARCHAR(40));

INSERT INTO rpa_ejecucion (
    correlation_id, proveedor, periodo_desde, periodo_hasta, fecha_ejecucion,
    estado, facturas_recibidas, facturas_procesadas, facturas_con_error, payload_crudo
)
OUTPUT inserted.id, inserted.periodo_desde, inserted.estado
    INTO @rpa (id, periodo_desde, estado)
VALUES
 (NEWID(), N'Portal Proveedores', '2026-06-01','2026-06-30','2026-06-18T06:00:00', N'CON_ERRORES', 40, 38, 2, N'["Comprobante 0003-00048321: formato de CUIT inválido.","Comprobante 0002-00098401: archivo PDF corrupto."]'),
 (NEWID(), N'Portal Proveedores', '2026-06-01','2026-06-30','2026-06-17T06:00:00', N'COMPLETADO',  40, 40, 0, NULL),
 (NEWID(), N'Portal Proveedores', '2026-05-01','2026-05-31','2026-06-10T06:00:00', N'COMPLETADO',  40, 40, 0, NULL),
 (NEWID(), N'Portal Proveedores', '2026-05-01','2026-05-31','2026-06-03T06:00:00', N'EN_CURSO',    25, 20, 0, NULL),
 (NEWID(), N'Portal Proveedores', '2026-04-01','2026-04-30','2026-05-27T06:00:00', N'COMPLETADO',  40, 40, 0, NULL),
 (NEWID(), N'Portal Proveedores', '2026-04-01','2026-04-30','2026-05-20T06:00:00', N'CON_ERRORES', 40, 39, 1, N'["Comprobante 0001-00011980: emisor no identificado."]');

DECLARE @rpaJunio BIGINT = (
    SELECT MIN(id)
    FROM @rpa
    WHERE periodo_desde = '2026-06-01'
      AND estado = N'CON_ERRORES'
);

/* =========================================================================
   4) FACTURAS
      @mesesHistoria = 1 -> solo 2026-06 (cantidad exacta por contrato)
      @mesesHistoria = 3 -> agrega 2026-04 y 2026-05 como historia
   ========================================================================= */
DECLARE @mesesHistoria INT = 1;          -- <<< CAMBIAR A 3 SI QUERES HISTORIA

DECLARE @periodos TABLE (
    orden INT, periodo DATE, fecha_emision DATE, seq_base INT, es_conciliado BIT
);

INSERT INTO @periodos (orden, periodo, fecha_emision, seq_base, es_conciliado)
VALUES
 (1, '2026-06-01', '2026-06-05', 48000, 1),
 (2, '2026-05-01', '2026-05-04', 47500, 0),
 (3, '2026-04-01', '2026-04-05', 47000, 0);

DELETE FROM @periodos WHERE orden > @mesesHistoria;

DECLARE @c      BIGINT;
DECLARE @imp    DECIMAL(18,2);
DECLARE @cuit   CHAR(11);
DECLARE @razon  NVARCHAR(200);
DECLARE @inm    BIGINT;
DECLARE @pv     INT = 3;
DECLARE @cat    INT;
DECLARE @rownum INT = 0;

DECLARE @cantFact   INT;
DECLARE @k          INT;
DECLARE @pct        INT;
DECLARE @acum       DECIMAL(18,2);
DECLARE @impFact    DECIMAL(18,2);
DECLARE @neto       DECIMAL(18,2);

DECLARE @pOrden     INT;
DECLARE @maxOrden   INT;
DECLARE @periodo    DATE;
DECLARE @fechaEmi   DATE;
DECLARE @seqBase    INT;
DECLARE @esConc     BIT;
DECLARE @impPeriodo DECIMAL(18,2);
DECLARE @tipoComp   SMALLINT;
DECLARE @estadoFac  NVARCHAR(40);
DECLARE @rpaId      BIGINT;
DECLARE @seq        INT;

SET @maxOrden = (SELECT MAX(orden) FROM @periodos);

DECLARE cur CURSOR LOCAL FAST_FORWARD FOR
    SELECT ct.id, ct.inmueble_id, cv.importe_mensual,
           lo.cuit, lo.razon_social, s.cant_facturas
    FROM #contratos_seed s
    JOIN contrato ct       ON ct.id = s.contrato_id
    JOIN contrato_valor cv ON cv.contrato_id = ct.id
                          AND cv.vigencia_hasta IS NULL
    JOIN locador lo        ON lo.id = ct.locador_id
    ORDER BY ct.id;

OPEN cur;
FETCH NEXT FROM cur INTO @c, @inm, @imp, @cuit, @razon, @cantFact;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- categoria del periodo conciliado: 0=sin factura, 1=OK, 2=dif <3%, 3=dif >3%
    SET @cat = CASE
        WHEN @rownum % 9 = 7 THEN 0
        WHEN @rownum % 4 = 2 THEN 3
        WHEN @rownum % 4 = 1 THEN 2
        ELSE 1
    END;

    SET @pOrden = 1;

    WHILE @pOrden <= @maxOrden
    BEGIN
        SELECT @periodo  = periodo,
               @fechaEmi = fecha_emision,
               @seqBase  = seq_base,
               @esConc   = es_conciliado
        FROM @periodos
        WHERE orden = @pOrden;

        IF @esConc = 1
        BEGIN
            SET @impPeriodo = CASE @cat
                WHEN 3 THEN @imp * 0.94
                WHEN 2 THEN @imp * 0.985
                ELSE @imp
            END;

            SET @tipoComp  = CASE WHEN @cat = 3 THEN 2 ELSE 1 END;
            SET @estadoFac = CASE WHEN @cat = 3 THEN N'CON_DIFERENCIA' ELSE N'COINCIDE' END;
            SET @rpaId     = @rpaJunio;
        END
        ELSE
        BEGIN
            SET @impPeriodo = @imp;
            SET @tipoComp   = 1;
            SET @estadoFac  = N'COINCIDE';
            SET @rpaId      = NULL;
        END

        IF NOT (@esConc = 1 AND @cat = 0)
           AND NOT EXISTS (
               SELECT 1 FROM factura
               WHERE contrato_id = @c
                 AND periodo_facturado = @periodo
           )
        BEGIN
            SET @acum = 0;
            SET @k    = 1;

            WHILE @k <= @cantFact
            BEGIN
                -- 1 -> 100 | 2 -> 50,50 | 3 -> 34,33,33
                SET @pct = CASE
                    WHEN @k = 1
                        THEN 100 - (100 / @cantFact) * (@cantFact - 1)
                    ELSE 100 / @cantFact
                END;

                -- la ultima factura absorbe el residuo
                SET @impFact = CASE
                    WHEN @k = @cantFact THEN @impPeriodo - @acum
                    ELSE ROUND(@impPeriodo * @pct / 100.0, 2)
                END;

                SET @acum = @acum + @impFact;
                SET @neto = ROUND(@impFact / 1.21, 2);
                SET @seq  = @seqBase + (@rownum * 4) + @k;

                INSERT INTO factura (
                    contrato_id, inmueble_id, cuit_emisor, razon_social,
                    tipo_comprobante_id, punto_venta, numero_comprobante,
                    fecha_emision, periodo_facturado,
                    importe_neto, importe_iva, importe_total,
                    cae, estado, origen, rpa_ejecucion_id
                )
                VALUES (
                    @c, @inm, @cuit, @razon, @tipoComp, @pv,
                    N'0003-' + RIGHT('00000000' + CAST(@seq AS NVARCHAR(10)), 8),
                    @fechaEmi, @periodo,
                    @neto, @impFact - @neto, @impFact,
                    N'7' + CAST(1234567890000 + @seq AS NVARCHAR(20)),
                    @estadoFac, N'RPA', @rpaId
                );

                SET @k = @k + 1;
            END
        END

        SET @pOrden = @pOrden + 1;
    END

    SET @rownum = @rownum + 1;
    FETCH NEXT FROM cur INTO @c, @inm, @imp, @cuit, @razon, @cantFact;
END

CLOSE cur;
DEALLOCATE cur;

/* =========================================================================
   5) FACTURAS SIN ASIGNAR (bandeja)  -  contrato_id NULL
   ========================================================================= */
INSERT INTO factura (
    cuit_emisor, razon_social, tipo_comprobante_id, punto_venta,
    numero_comprobante, fecha_emision, periodo_facturado,
    importe_neto, importe_iva, importe_total, estado, origen, rpa_ejecucion_id
)
VALUES
 ('30712345672', N'Propietaria del Plata SRL', 1, 3, N'0003-00047989', '2026-05-04','2026-05-01', 296033.06, 62166.94, 358200.00, N'SIN_ASIGNAR', N'RPA', @rpaJunio),
 ('30685412309', N'Inversiones del Sur SA',    1, 1, N'0001-00012044', '2026-06-06','2026-06-01', 340495.87, 71504.13, 412000.00, N'SIN_ASIGNAR', N'RPA', @rpaJunio),
 ('20259876541', N'Pérez Carlos Alberto',      2, 2, N'0002-00098231', '2026-06-04','2026-06-01', 239256.20, 50243.80, 289500.00, N'SIN_ASIGNAR', N'RPA', @rpaJunio),
 ('30687412309', N'Fideicomiso Norte',         1, 4, N'0004-00003321', '2026-05-09','2026-05-01', 414297.52, 87002.48, 501300.00, N'SIN_ASIGNAR', N'RPA', @rpaJunio),
 ('33710293845', N'Grupo Inmobiliario Cuyo SA',1, 1, N'0001-00012099', '2026-06-07','2026-06-01', 162809.92, 34190.08, 197000.00, N'SIN_ASIGNAR', N'RPA', @rpaJunio),
 ('30712345672', N'Propietaria del Plata SRL', 1, 3, N'0003-00047890', '2026-04-04','2026-04-01', 296033.06, 62166.94, 358200.00, N'SIN_ASIGNAR', N'RPA', @rpaJunio);

/* =========================================================================
   6) CONCILIACION DEL PERIODO 2026-06
   ========================================================================= */
DECLARE @concPeriodo DATE = '2026-06-01';

DECLARE @c2         BIGINT;
DECLARE @esp        DECIMAL(18,2);
DECLARE @fact       DECIMAL(18,2);
DECLARE @cnt        INT;
DECLARE @estadoConc NVARCHAR(40);
DECLARE @concId     BIGINT;
DECLARE @fid        BIGINT;

DECLARE cur2 CURSOR LOCAL FAST_FORWARD FOR
    SELECT ct.id, cv.importe_mensual
    FROM contrato ct
    JOIN contrato_valor cv ON cv.contrato_id = ct.id
                          AND cv.vigencia_hasta IS NULL
    ORDER BY ct.id;

OPEN cur2;
FETCH NEXT FROM cur2 INTO @c2, @esp;

WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @fact = ISNULL(SUM(importe_total), 0),
           @cnt  = COUNT(*)
    FROM factura
    WHERE contrato_id = @c2
      AND periodo_facturado = @concPeriodo;

    SET @estadoConc = CASE
        WHEN @cnt = 0 THEN N'SIN_FACTURA'
        WHEN @fact = @esp THEN N'OK'
        WHEN ABS(@fact - @esp) <= @esp * 0.03 THEN N'OK_CON_DIF'
        ELSE N'CON_DIFERENCIA'
    END;

    INSERT INTO conciliacion (
        contrato_id, periodo, importe_esperado, importe_facturado,
        estado, revisada_por, revisada_en
    )
    VALUES (
        @c2, @concPeriodo, @esp, @fact, @estadoConc,
        CASE WHEN @estadoConc = N'CON_DIFERENCIA' THEN 1 ELSE NULL END,
        CASE WHEN @estadoConc = N'CON_DIFERENCIA' THEN '2026-06-12T10:41:00' ELSE NULL END
    );

    SET @concId = CONVERT(BIGINT, SCOPE_IDENTITY());

    INSERT INTO conciliacion_factura (conciliacion_id, factura_id)
    SELECT @concId, id
    FROM factura
    WHERE contrato_id = @c2
      AND periodo_facturado = @concPeriodo;

    IF @estadoConc IN (N'CON_DIFERENCIA', N'OK_CON_DIF')
    BEGIN
        SET @fid = NULL;

        SELECT @fid = MIN(id)
        FROM factura
        WHERE contrato_id = @c2
          AND periodo_facturado = @concPeriodo;

        IF @fid IS NOT NULL
        BEGIN
            INSERT INTO conciliacion_diferencia (
                conciliacion_id, factura_id, tipo, severidad,
                valor_esperado, valor_obtenido
            )
            VALUES (
                @concId, @fid, N'IMPORTE',
                CASE WHEN @estadoConc = N'CON_DIFERENCIA' THEN N'ALTA' ELSE N'BAJA' END,
                CAST(@esp AS NVARCHAR(40)), CAST(@fact AS NVARCHAR(40))
            );

            IF EXISTS (
                SELECT 1 FROM factura
                WHERE id = @fid AND tipo_comprobante_id = 2
            )
                INSERT INTO conciliacion_diferencia (
                    conciliacion_id, factura_id, tipo, severidad,
                    valor_esperado, valor_obtenido
                )
                VALUES (
                    @concId, @fid, N'TIPO_COMPROBANTE', N'MEDIA',
                    N'Factura A', N'Factura B'
                );
        END
    END

    FETCH NEXT FROM cur2 INTO @c2, @esp;
END

CLOSE cur2;
DEALLOCATE cur2;

DROP TABLE #contratos_seed;
GO

/* ---------- Auditoría ---------- */
INSERT INTO auditoria (fecha, entidad, entidad_id, accion, descripcion, usuario_id, contrato_ref) VALUES
 ('2026-06-18T09:14:00', N'conciliacion', N'1', N'ACEPTAR_DIFERENCIA', N'Aceptó diferencia con justificación', 1, N'B0507 · Sucursal G'),
 ('2026-06-17T16:40:00', N'conciliacion', N'2', N'APROBAR',            N'Aprobó resolución del analista', 2, N'B0512 · CDD L'),
 ('2026-06-17T11:02:00', N'contrato',     N'1', N'EDITAR',             N'Editó índice de ajuste (ICL → IPC)', 3, N'B0501'),
 ('2026-06-16T08:55:00', N'rpa_ejecucion',N'1', N'INGESTA',            N'Corrida de ingesta finalizada con 2 errores', 5, N'—'),
 ('2026-06-15T14:20:00', N'conciliacion', N'3', N'DEVOLVER',           N'Devolvió diferencia al analista', 2, N'B0509'),
 ('2026-06-12T10:05:00', N'contrato_valor',N'1',N'AJUSTE',             N'Registró ajuste de importe mensual', 1, N'B0501');

/* ---------- Historial de cambios sobre el contrato 1 (para la pestaña) ---------- */
INSERT INTO auditoria (fecha, entidad, entidad_id, accion, descripcion, datos_antes, datos_despues, usuario_id, contrato_ref) VALUES
 ('2026-06-12T00:00:00', N'contrato', N'1', N'EDITAR', N'Importe mensual', N'333500', N'361000', 1, N'B0501'),
 ('2024-04-02T00:00:00', N'contrato', N'1', N'EDITAR', N'Índice de ajuste', N'ICL', N'IPC', 3, N'B0501'),
 ('2024-01-15T00:00:00', N'contrato', N'1', N'EDITAR', N'Tolerancia', N'±5%', N'±3%', 1, N'B0501');

SET NOCOUNT OFF;
