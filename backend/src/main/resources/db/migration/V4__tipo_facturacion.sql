IF COL_LENGTH('contrato', 'tipo_facturacion') IS NULL
    ALTER TABLE contrato ADD tipo_facturacion NVARCHAR(30) NULL;
