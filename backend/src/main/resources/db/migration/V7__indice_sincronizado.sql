IF COL_LENGTH('indice_ajuste', 'sincronizado_en') IS NULL
    ALTER TABLE indice_ajuste ADD sincronizado_en DATETIME2 NULL;
GO
