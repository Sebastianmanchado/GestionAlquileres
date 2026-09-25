package com.correoargentino.sga.web;

import com.correoargentino.sga.dto.FacturaRequest;
import com.correoargentino.sga.service.RpaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rpa")
public class RpaController {

    private final RpaService service;

    public RpaController(RpaService service) {
        this.service = service;
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() {
        return service.runs();
    }

    @Operation(
        summary = "Crear factura",
        description = "Recibe una factura en formato JSON y la registra en el sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Factura creada correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación")
    })

    @PostMapping("/factura")
    public ResponseEntity<Map<String, Object>> crearFactura(

        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Datos completos de la factura",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = FacturaRequest.class),
                examples = @ExampleObject(
                    name = "Factura Ejemplo",
                    value = """
                    {
                    "emisor": {
                        "razonSocial": "Inmobiliaria Andina SRL",
                        "cuit": "30687412308",
                        "condicionIva": "RI"
                    },
                    "comprobante": {
                        "tipo": "A",
                        "puntoVenta": "00004",
                        "numero": "00000101",
                        "fechaEmision": "2026-08-04",
                        "fechaVencimientoPago": "2026-08-15",
                        "periodoFacturado": {
                        "desde": "2026-07-01",
                        "hasta": "2026-07-31"
                        },
                        "moneda": "ARS",
                        "importes": {
                        "neto": 298347.11,
                        "alicuotaIva": 21.0,
                        "iva": 62652.89,
                        "otrosTributos": 0.0,
                        "total": 361000.0
                        }
                    },
                    "datosFiscales": {
                        "cae": "74125896301478",
                        "fechaVencimientoCae": "2026-08-14"
                    },
                    "datosPago": {
                        "cbu": "0170299420000001234567",
                        "aliasCbu": "ANDINA.ALQUILER"
                    },
                    "items": [
                        {
                        "descripcion": "Alquiler mensual Sucursal Centro",
                        "cantidad": 1.0,
                        "unidadMedida": "mes"
                        }
                    ]
                    }
                    """
                )
            )
        )
        @org.springframework.web.bind.annotation.RequestBody FacturaRequest request

    ) throws BadRequestException {

        long facturaId = service.crearFactura(request);

        return ResponseEntity.ok(
            Map.of(
                "facturaId", facturaId,
                "datos", request
            )
        );
    }
}
