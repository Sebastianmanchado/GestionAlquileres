-- Los contratos marcados como próximos a vencer pasan a vigente o vencido según la fecha.
UPDATE contrato
   SET estado_contrato_id = CASE
           WHEN fecha_vencimiento < CAST(GETDATE() AS DATE) THEN 3
           ELSE 1
       END
 WHERE estado_contrato_id = 2;
