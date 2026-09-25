IF COL_LENGTH('indice_ajuste', 'codigo_serie') IS NULL
    ALTER TABLE indice_ajuste ADD codigo_serie NVARCHAR(80) NULL;
GO

IF COL_LENGTH('indice_valor', 'origen') IS NULL
    ALTER TABLE indice_valor ADD origen NVARCHAR(10) NOT NULL
        CONSTRAINT df_indice_valor_origen DEFAULT N'SEED';
GO

UPDATE indice_ajuste
   SET codigo_serie = N'148.3_INIVELNAL_DICI_M_26'
 WHERE codigo = N'IPC'
   AND (codigo_serie IS NULL OR codigo_serie = N'');

UPDATE indice_ajuste
   SET codigo_serie = N'40'
 WHERE codigo = N'ICL'
   AND (codigo_serie IS NULL OR codigo_serie = N'');
