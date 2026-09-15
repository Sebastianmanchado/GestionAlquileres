/* =========================================================================
   SGA Alquileres - Esquema inicial (MSSQL)
   Basado en der_alquileres.puml
   ========================================================================= */

/* ---------- Maestros / Geografia ---------- */
CREATE TABLE provincia (
    id            SMALLINT       NOT NULL PRIMARY KEY,
    nombre        NVARCHAR(120)  NOT NULL UNIQUE,
    codigo_indec  CHAR(2)        NULL
);

CREATE TABLE region (
    id      SMALLINT      NOT NULL PRIMARY KEY,
    nombre  NVARCHAR(120) NOT NULL UNIQUE,
    activo  BIT           NOT NULL DEFAULT 1
);

CREATE TABLE localidad (
    id             INT            NOT NULL IDENTITY(1,1) PRIMARY KEY,
    provincia_id   SMALLINT       NOT NULL,
    nombre         NVARCHAR(160)  NOT NULL,
    codigo_postal  NVARCHAR(12)   NULL,
    CONSTRAINT fk_localidad_provincia FOREIGN KEY (provincia_id) REFERENCES provincia(id)
);

CREATE TABLE destino_uso (
    id      SMALLINT      NOT NULL PRIMARY KEY,
    codigo  NVARCHAR(40)  NOT NULL UNIQUE,
    nombre  NVARCHAR(120) NOT NULL
);

CREATE TABLE titulo_acreditante (
    id           SMALLINT       NOT NULL PRIMARY KEY,
    codigo       NVARCHAR(40)   NOT NULL UNIQUE,
    descripcion  NVARCHAR(200)  NOT NULL
);

CREATE TABLE centro_costo (
    id           INT            NOT NULL PRIMARY KEY,
    codigo       NVARCHAR(40)   NOT NULL UNIQUE,
    descripcion  NVARCHAR(200)  NOT NULL
);

/* ---------- Inmuebles ---------- */
CREATE TABLE inmueble (
    id                     BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    nis                    NVARCHAR(40)   NOT NULL,
    denominacion           NVARCHAR(200)  NOT NULL,
    direccion              NVARCHAR(250)  NULL,
    localidad_id           INT            NULL,
    region_id              SMALLINT       NULL,
    centro_costo_id        INT            NULL,
    titulo_acreditante_id  SMALLINT       NULL,
    superficie_cubierta_m2 DECIMAL(12,2)  NULL,
    superficie_terreno_m2  DECIMAL(12,2)  NULL,
    responsable            NVARCHAR(160)  NULL,
    responsable_email      NVARCHAR(160)  NULL,
    responsable_telefono   NVARCHAR(60)   NULL,
    activo                 BIT            NOT NULL DEFAULT 1,
    CONSTRAINT fk_inmueble_localidad  FOREIGN KEY (localidad_id) REFERENCES localidad(id),
    CONSTRAINT fk_inmueble_region     FOREIGN KEY (region_id) REFERENCES region(id),
    CONSTRAINT fk_inmueble_cc         FOREIGN KEY (centro_costo_id) REFERENCES centro_costo(id),
    CONSTRAINT fk_inmueble_titulo     FOREIGN KEY (titulo_acreditante_id) REFERENCES titulo_acreditante(id)
);

CREATE TABLE inmueble_destino (
    inmueble_id  BIGINT   NOT NULL,
    destino_id   SMALLINT NOT NULL,
    CONSTRAINT pk_inmueble_destino PRIMARY KEY (inmueble_id, destino_id),
    CONSTRAINT fk_id_inmueble FOREIGN KEY (inmueble_id) REFERENCES inmueble(id),
    CONSTRAINT fk_id_destino  FOREIGN KEY (destino_id) REFERENCES destino_uso(id)
);

/* ---------- Partes ---------- */
CREATE TABLE locador (
    id            BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    tipo_persona  NVARCHAR(20)   NOT NULL DEFAULT 'JURIDICA',
    razon_social  NVARCHAR(200)  NOT NULL,
    cuit          CHAR(11)       NOT NULL UNIQUE,
    email         NVARCHAR(160)  NULL,
    telefono      NVARCHAR(60)   NULL,
    cbu           VARCHAR(22)    NULL,
    activo        BIT            NOT NULL DEFAULT 1
);

CREATE TABLE acreedor_sap (
    id           BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    codigo_sap   NVARCHAR(40)   NOT NULL UNIQUE,
    locador_id   BIGINT         NOT NULL,
    descripcion  NVARCHAR(200)  NULL,
    CONSTRAINT fk_acreedor_locador FOREIGN KEY (locador_id) REFERENCES locador(id)
);

/* ---------- Contratos (catalogos) ---------- */
CREATE TABLE tipo_contrato (
    id      SMALLINT      NOT NULL PRIMARY KEY,
    codigo  NVARCHAR(60)  NOT NULL UNIQUE,
    nombre  NVARCHAR(120) NOT NULL
);

CREATE TABLE estado_contrato (
    id       SMALLINT      NOT NULL PRIMARY KEY,
    codigo   NVARCHAR(40)  NOT NULL UNIQUE,
    nombre   NVARCHAR(80)  NOT NULL,
    es_final BIT           NOT NULL DEFAULT 0
);

CREATE TABLE tipo_comprobante (
    id          SMALLINT      NOT NULL PRIMARY KEY,
    codigo      NVARCHAR(40)  NOT NULL UNIQUE,
    nombre      NVARCHAR(80)  NOT NULL,
    codigo_afip NVARCHAR(10)  NULL
);

CREATE TABLE indice_ajuste (
    id      SMALLINT      NOT NULL PRIMARY KEY,
    codigo  NVARCHAR(20)  NOT NULL UNIQUE,
    nombre  NVARCHAR(120) NOT NULL,
    fuente  NVARCHAR(120) NULL
);

CREATE TABLE indice_valor (
    id         BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    indice_id  SMALLINT      NOT NULL,
    periodo    DATE          NOT NULL,
    valor      DECIMAL(14,4) NOT NULL,
    CONSTRAINT fk_indicevalor_indice FOREIGN KEY (indice_id) REFERENCES indice_ajuste(id),
    CONSTRAINT uk_indicevalor UNIQUE (indice_id, periodo)
);


/* ---------- Contrato ---------- */
CREATE TABLE contrato (
    id                     BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    numero                 NVARCHAR(40)   NOT NULL UNIQUE,
    inmueble_id            BIGINT         NOT NULL,
    locador_id             BIGINT         NOT NULL,
    acreedor_sap_id        BIGINT         NULL,
    tipo_contrato_id       SMALLINT       NOT NULL,
    estado_contrato_id     SMALLINT       NOT NULL,
    contrato_anterior_id   BIGINT         NULL,
    fecha_inicio           DATE           NOT NULL,
    fecha_vencimiento      DATE           NOT NULL,
    moneda                 CHAR(3)        NOT NULL DEFAULT 'ARS',
    importe_inicial        DECIMAL(18,2)  NOT NULL DEFAULT 0,
    deposito_garantia      DECIMAL(18,2)  NULL,
    indice_ajuste_id       SMALLINT       NULL,
    periodicidad_ajuste    NVARCHAR(20)   NULL,
    tipo_comprobante_id    SMALLINT       NULL,
    tolerancia_importe_pct DECIMAL(6,2)   NULL,
    observaciones          NVARCHAR(MAX)  NULL,
    creado_en              DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    cantidad_facturas      SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_contrato_inmueble  FOREIGN KEY (inmueble_id) REFERENCES inmueble(id),
    CONSTRAINT fk_contrato_locador   FOREIGN KEY (locador_id) REFERENCES locador(id),
    CONSTRAINT fk_contrato_acreedor  FOREIGN KEY (acreedor_sap_id) REFERENCES acreedor_sap(id),
    CONSTRAINT fk_contrato_tipo      FOREIGN KEY (tipo_contrato_id) REFERENCES tipo_contrato(id),
    CONSTRAINT fk_contrato_estado    FOREIGN KEY (estado_contrato_id) REFERENCES estado_contrato(id),
    CONSTRAINT fk_contrato_anterior  FOREIGN KEY (contrato_anterior_id) REFERENCES contrato(id),
    CONSTRAINT fk_contrato_indice    FOREIGN KEY (indice_ajuste_id) REFERENCES indice_ajuste(id),
    CONSTRAINT fk_contrato_comp      FOREIGN KEY (tipo_comprobante_id) REFERENCES tipo_comprobante(id)
);

CREATE TABLE factura_planificada (
    id                      BIGINT          NOT NULL IDENTITY(1,1) PRIMARY KEY,
    contrato_id             BIGINT          NOT NULL,
    porcentaje_esperado     SMALLINT        NOT NULL,
    monto_esperado          DECIMAL(18,2)   NOT NULL,
    estado               NVARCHAR(40)  NOT NULL,
    CONSTRAINT fk_contrato_id  FOREIGN KEY (contrato_id) REFERENCES contrato(id),
);

CREATE TABLE contrato_valor (
    id                   BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    contrato_id          BIGINT        NOT NULL,
    vigencia_desde       DATE          NOT NULL,
    vigencia_hasta       DATE          NULL,
    importe_mensual      DECIMAL(18,2) NOT NULL,
    origen               NVARCHAR(40)  NOT NULL DEFAULT 'CONTRATO',
    indice_id            SMALLINT      NULL,
    coeficiente_aplicado DECIMAL(14,4) NULL,
    CONSTRAINT fk_cv_contrato FOREIGN KEY (contrato_id) REFERENCES contrato(id),
    CONSTRAINT fk_cv_indice   FOREIGN KEY (indice_id) REFERENCES indice_ajuste(id)
);

/* ---------- RPA / Facturacion ---------- */
CREATE TABLE rpa_ejecucion (
    id                  BIGINT           NOT NULL IDENTITY(1,1) PRIMARY KEY,
    correlation_id      UNIQUEIDENTIFIER NOT NULL UNIQUE,
    proveedor           NVARCHAR(80)     NULL,
    periodo_desde       DATE             NULL,
    periodo_hasta       DATE             NULL,
    fecha_ejecucion     DATETIME2        NOT NULL DEFAULT SYSUTCDATETIME(),
    estado              NVARCHAR(30)     NOT NULL,
    facturas_recibidas  INT              NOT NULL DEFAULT 0,
    facturas_procesadas INT              NOT NULL DEFAULT 0,
    facturas_con_error  INT              NOT NULL DEFAULT 0,
    payload_crudo       NVARCHAR(MAX)    NULL
);

CREATE TABLE factura (
    id                 BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    contrato_id        BIGINT         NULL,
    inmueble_id        BIGINT         NULL,
    cuit_emisor        CHAR(11)       NOT NULL,
    razon_social       NVARCHAR(200)  NULL,
    tipo_comprobante_id SMALLINT      NULL,
    punto_venta        INT            NULL,
    numero_comprobante NVARCHAR(40)   NULL,
    fecha_emision      DATE           NULL,
    periodo_facturado  DATE           NULL,
    importe_neto       DECIMAL(18,2)  NULL,
    importe_iva        DECIMAL(18,2)  NULL,
    importe_total      DECIMAL(18,2)  NOT NULL DEFAULT 0,
    cae                NVARCHAR(40)   NULL,
    estado             NVARCHAR(40)   NOT NULL DEFAULT 'SIN_ASIGNAR',
    origen             NVARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    rpa_ejecucion_id   BIGINT         NULL,
    observaciones      NVARCHAR(MAX)  NULL,
    datos_extraidos    NVARCHAR(MAX)  NULL,
    creado_en          DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_factura_contrato FOREIGN KEY (contrato_id) REFERENCES contrato(id),
    CONSTRAINT fk_factura_inmueble FOREIGN KEY (inmueble_id) REFERENCES inmueble(id),
    CONSTRAINT fk_factura_comp     FOREIGN KEY (tipo_comprobante_id) REFERENCES tipo_comprobante(id),
    CONSTRAINT fk_factura_rpa      FOREIGN KEY (rpa_ejecucion_id) REFERENCES rpa_ejecucion(id),
);

/* ---------- Archivos ---------- */
CREATE TABLE archivo (
    id               BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    uuid             UNIQUEIDENTIFIER NOT NULL UNIQUE,
    factura_id       BIGINT NOT NULL,
    nombre_original  NVARCHAR(250)  NOT NULL,
    ruta_relativa    NVARCHAR(400)  NOT NULL UNIQUE,
    mime_type        NVARCHAR(120)  NULL,
    tamano_bytes     BIGINT         NULL,
    sha256           CHAR(64)       NULL,
    storage_backend  NVARCHAR(40)   NOT NULL DEFAULT 'FILESYSTEM',
    creado_en        DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_factura_id  FOREIGN KEY (factura_id) REFERENCES factura(id)
);

CREATE TABLE contrato_seguro (
    id              BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    contrato_id     BIGINT        NOT NULL,
    tipo            NVARCHAR(40)  NOT NULL,
    nro_poliza      NVARCHAR(60)  NULL,
    suma_asegurada  DECIMAL(18,2) NULL,
    vigencia_hasta  DATE          NULL,
    archivo_id      BIGINT        NULL,
    CONSTRAINT fk_seguro_contrato FOREIGN KEY (contrato_id) REFERENCES contrato(id),
    CONSTRAINT fk_seguro_archivo  FOREIGN KEY (archivo_id) REFERENCES archivo(id)
);

/* ---------- Conciliacion ---------- */
CREATE TABLE conciliacion (
    id                 BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    contrato_id        BIGINT         NOT NULL,
    periodo            DATE           NOT NULL,
    importe_esperado   DECIMAL(18,2)  NOT NULL DEFAULT 0,
    importe_facturado  DECIMAL(18,2)  NOT NULL DEFAULT 0,
    diferencia         AS (importe_facturado - importe_esperado) PERSISTED,
    estado             NVARCHAR(40)   NOT NULL DEFAULT 'PENDIENTE',
    comentario         NVARCHAR(MAX)  NULL,
    revisada_por       BIGINT         NULL,
    revisada_en        DATETIME2      NULL,
    creado_en          DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_conc_contrato FOREIGN KEY (contrato_id) REFERENCES contrato(id),
    CONSTRAINT uk_conc UNIQUE (contrato_id, periodo)
);

CREATE TABLE conciliacion_factura (
    conciliacion_id BIGINT NOT NULL,
    factura_id      BIGINT NOT NULL,
    CONSTRAINT pk_conc_factura PRIMARY KEY (conciliacion_id, factura_id),
    CONSTRAINT fk_cf_conc    FOREIGN KEY (conciliacion_id) REFERENCES conciliacion(id),
    CONSTRAINT fk_cf_factura FOREIGN KEY (factura_id) REFERENCES factura(id)
);

CREATE TABLE conciliacion_diferencia (
    id              BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    conciliacion_id BIGINT        NOT NULL,
    factura_id      BIGINT        NULL,
    tipo            NVARCHAR(40)  NOT NULL,
    severidad       NVARCHAR(20)  NOT NULL DEFAULT 'MEDIA',
    valor_esperado  NVARCHAR(200) NULL,
    valor_obtenido  NVARCHAR(200) NULL,
    CONSTRAINT fk_cd_conc    FOREIGN KEY (conciliacion_id) REFERENCES conciliacion(id),
    CONSTRAINT fk_cd_factura FOREIGN KEY (factura_id) REFERENCES factura(id)
);

/* ---------- Identidad y Auditoria ---------- */
CREATE TABLE usuario (
    id               BIGINT           NOT NULL IDENTITY(1,1) PRIMARY KEY,
    keycloak_subject UNIQUEIDENTIFIER NULL,
    username         NVARCHAR(120)    NOT NULL UNIQUE,
    nombre           NVARCHAR(160)    NULL,
    email            NVARCHAR(160)    NULL,
    activo           BIT              NOT NULL DEFAULT 1
);

CREATE TABLE usuario_rol_cache (
    usuario_id BIGINT       NOT NULL,
    rol        NVARCHAR(40) NOT NULL,
    CONSTRAINT pk_usuario_rol PRIMARY KEY (usuario_id, rol),
    CONSTRAINT fk_ur_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TABLE auditoria (
    id            BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    fecha         DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
    entidad       NVARCHAR(80)   NOT NULL,
    entidad_id    NVARCHAR(60)   NULL,
    accion        NVARCHAR(40)   NOT NULL,
    descripcion   NVARCHAR(400)  NULL,
    datos_antes   NVARCHAR(MAX)  NULL,
    datos_despues NVARCHAR(MAX)  NULL,
    usuario_id    BIGINT         NULL,
    contrato_ref  NVARCHAR(120)  NULL,
    CONSTRAINT fk_aud_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TABLE notificacion (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    texto NVARCHAR(500) NOT NULL,
    tiempo NVARCHAR(100) NULL,
    creado_en DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

/* ---------- Indices utiles ---------- */
CREATE INDEX ix_contrato_estado      ON contrato(estado_contrato_id);
CREATE INDEX ix_contrato_venc        ON contrato(fecha_vencimiento);
CREATE INDEX ix_factura_estado       ON factura(estado);
CREATE INDEX ix_factura_contrato     ON factura(contrato_id);
CREATE INDEX ix_conc_periodo         ON conciliacion(periodo);
CREATE INDEX ix_cv_contrato          ON contrato_valor(contrato_id);
CREATE INDEX ix_auditoria_fecha      ON auditoria(fecha);
