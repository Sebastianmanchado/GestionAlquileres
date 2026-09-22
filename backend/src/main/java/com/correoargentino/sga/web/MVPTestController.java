package com.correoargentino.sga.web;

import java.util.List;
import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.correoargentino.sga.service.TestService;

@RestController
@RequestMapping("/api")
public class MVPTestController {

    private final TestService service;

    public MVPTestController(TestService service) {
        this.service = service;
    }

    @GetMapping("/test/factura-proveedor-inexistente-B0600")
    public Map<String, Object> test1() throws BadRequestException {

        Map<String, Object> mock = Map.of(
            "emisor", Map.of(
                "razonSocial", "Inmobiliaria Andina SRL",
                "cuit", "30687412308", // CUIT inexistente en el sistema
                "condicionIva", "RI"
            ),

            "comprobante", Map.of(
                "tipo", "A",
                "puntoVenta", "00004",
                "numero", "00000101",
                "fechaEmision", "2026-08-04",
                "fechaVencimientoPago", "2026-08-15",

                "periodoFacturado", Map.of(
                    "desde", "2026-07-01",
                    "hasta", "2026-07-31"
                ),

                "moneda", "ARS",

                "importes", Map.of(
                    "neto", 298347.11,
                    "alicuotaIva", 21.0,
                    "iva", 62652.89,
                    "otrosTributos", 0.00,
                    "total", 361000.00
                )
            ),

            "datosFiscales", Map.of(
                "cae", "74125896301478",
                "fechaVencimientoCae", "2026-08-14"
            ),

            "datosPago", Map.of(
                "cbu", "0170299420000001234567",
                "aliasCbu", "ANDINA.ALQUILER"
            ),

            "items", List.of(
                Map.of(
                    "descripcion",
                    "Alquiler mensual Sucursal Centro",
                    "cantidad", 1.00,
                    "unidadMedida", "mes"
                )
            )
        );

        long facturaId =
            service.crearFactura(mock);

        return Map.of(
            "facturaId", facturaId,
            "datos", mock
        );
    }


    @GetMapping("/test/factura-monto-justo-B0601")
    public Map<String, Object> test2() throws BadRequestException {

        Map<String, Object> mock = Map.of(
            "emisor", Map.of(
                "razonSocial", "Alquileres del Litoral SA",
                "cuit", "11111111111", // CUIT existente
                "condicionIva", "RI"
            ),

            "comprobante", Map.of(
                "tipo", "A",
                "puntoVenta", "00007",
                "numero", "00000220",
                "fechaEmision", "2026-08-05",
                "fechaVencimientoPago", "2026-08-18",

                "periodoFacturado", Map.of(
                    "desde", "2026-07-01",
                    "hasta", "2026-07-31"
                ),

                "moneda", "ARS",

                "importes", Map.of(
                    "neto", 239256.20,
                    "alicuotaIva", 21.0,
                    "iva", 50243.80,
                    "otrosTributos", 0.00,
                    "total", 289500.00
                )
            ),

            "datosFiscales", Map.of(
                "cae", "85214796325814",
                "fechaVencimientoCae", "2026-08-15"
            ),

            "datosPago", Map.of(
                "cbu", "0070081820000009876543",
                "aliasCbu", "LITORAL.RENTAS"
            ),

            "items", List.of(
                Map.of(
                    "descripcion", "Alquiler mensual Sucursal H",
                    "cantidad", 1.00,
                    "unidadMedida", "mes"
                )
            )
        );

        long facturaId =
            service.crearFactura(mock);

        return Map.of(
            "facturaId", facturaId,
            "datos", mock
        );
    }


    @GetMapping("/test/factura-monto-aceptable-B0602")
    public Map<String, Object> test3() throws BadRequestException {

        Map<String, Object> mock = Map.of(
            "emisor", Map.of(
                "razonSocial", "Propietario 2",
                "cuit", "22222222222",
                "condicionIva", "RI"
            ),

            "comprobante", Map.of(
                "tipo", "A",
                "puntoVenta", "00003",
                "numero", "00048010",
                "fechaEmision", "2026-08-04",
                "fechaVencimientoPago", "2026-08-15",

                "periodoFacturado", Map.of(
                    "desde", "2026-07-01",
                    "hasta", "2026-07-31"
                ),

                "moneda", "ARS",

                "importes", Map.of(
                    "neto", 279486.20,
                    "alicuotaIva", 21.0,
                    "iva", 74293.80,
                    "otrosTributos", 0.00,
                    "total", 353780.00
                )
            ),

            "datosFiscales", Map.of(
                "cae", "11223344556677",
                "fechaVencimientoCae", "2026-08-14"
            ),

            "datosPago", Map.of(
                "cbu", "0110599520000011122233",
                "aliasCbu", "PROPIETARIO2.ALQ"
            ),

            "items", List.of(
                Map.of(
                    "descripcion", "Alquiler mensual",
                    "cantidad", 1.00,
                    "unidadMedida", "mes"
                )
            )
        );

        long facturaId =
            service.crearFactura(mock);

        return Map.of(
            "facturaId", facturaId,
            "datos", mock
        );
    }


    @GetMapping("/test/factura-monto-incorrecto-B0603")
    public Map<String, Object> test4() throws BadRequestException {

        Map<String, Object> mock = Map.of(
            "emisor", Map.of(
                "razonSocial", "Propietario 4",
                "cuit", "33333333333",
                "condicionIva", "RI"
            ),

            "comprobante", Map.of(
                "tipo", "A",
                "puntoVenta", "00002",
                "numero", "00098300",
                "fechaEmision", "2026-08-03",
                "fechaVencimientoPago", "2026-08-16",

                "periodoFacturado", Map.of(
                    "desde", "2026-07-01",
                    "hasta", "2026-07-31"
                ),

                "moneda", "ARS",

                "importes", Map.of(
                    "neto", 200000.00,
                    "alicuotaIva", 0.00,
                    "iva", 0.00,
                    "otrosTributos", 0.00,
                    "total", 200000.00
                )
            ),

            "datosFiscales", Map.of(
                "cae", "99887766554433",
                "fechaVencimientoCae", "2026-08-13"
            ),

            "datosPago", Map.of(
                "cbu", "2850596540000044455566",
                "aliasCbu", "PROPIETARIO4.ALQ"
            ),

            "items", List.of(
                Map.of(
                    "descripcion", "Alquiler mensual",
                    "cantidad", 1.00,
                    "unidadMedida", "mes"
                )
            )
        );

        long facturaId = service.crearFactura(mock);

        return Map.of(
            "facturaId", facturaId,
            "datos", mock
        );
    }
}