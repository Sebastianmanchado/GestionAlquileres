IF COL_LENGTH('factura', 'fecha_vto_cae') IS NULL
    ALTER TABLE factura ADD fecha_vto_cae DATE NULL;
GO

IF COL_LENGTH('factura', 'moneda') IS NULL
    ALTER TABLE factura ADD moneda CHAR(3) NULL;
GO

IF COL_LENGTH('contrato', 'ceco_sap') IS NULL
    ALTER TABLE contrato ADD ceco_sap NVARCHAR(20) NULL;
GO

IF COL_LENGTH('contrato', 'division_sap') IS NULL
    ALTER TABLE contrato ADD division_sap NVARCHAR(20) NULL;
GO

IF COL_LENGTH('contrato', 'cuenta_gasto') IS NULL
    ALTER TABLE contrato ADD cuenta_gasto NVARCHAR(40) NULL;
GO

IF COL_LENGTH('contrato', 'indicador_impuesto') IS NULL
    ALTER TABLE contrato ADD indicador_impuesto NVARCHAR(10) NULL;
GO

IF OBJECT_ID('dbo.asiento_secuencia', 'U') IS NULL
BEGIN
    CREATE TABLE asiento_secuencia (
        id     INT NOT NULL PRIMARY KEY,
        ultimo INT NOT NULL
    );
    INSERT INTO asiento_secuencia (id, ultimo) VALUES (1, 0);
END
GO

IF OBJECT_ID('dbo.asiento_sap', 'U') IS NULL
BEGIN
    CREATE TABLE asiento_sap (
        id               BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
        numero           INT            NOT NULL,
        factura_id       BIGINT         NOT NULL,
        conciliacion_id  BIGINT         NOT NULL,
        xml_enviado      NVARCHAR(MAX)  NOT NULL,
        estado           NVARCHAR(20)   NOT NULL,
        respuesta        NVARCHAR(MAX)  NULL,
        fecha_envio      DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_asiento_factura FOREIGN KEY (factura_id) REFERENCES factura(id),
        CONSTRAINT fk_asiento_conc FOREIGN KEY (conciliacion_id) REFERENCES conciliacion(id)
    );
    CREATE UNIQUE INDEX ux_asiento_sap_factura_enviado
        ON asiento_sap(factura_id)
        WHERE estado = N'ENVIADO';
END
GO
