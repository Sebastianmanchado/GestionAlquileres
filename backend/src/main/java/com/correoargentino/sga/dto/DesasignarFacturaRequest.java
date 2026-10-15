package com.correoargentino.sga.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos para desasignar una factura de su contrato")
public record DesasignarFacturaRequest(

    @Schema(description = "Id del contrato que tenía asignada la factura", example = "15")
    @NotNull(message = "contratoId es obligatorio")
    Long contratoId,

    @Schema(description = "Número de comprobante (se usa en la notificación)", example = "0003-00045821")
    @NotBlank(message = "comprobante es obligatorio")
    String comprobante
) {}