package com.correoargentino.sga.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    private final InvoiceService invoiceService;

    public TestService(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @SuppressWarnings("unchecked")
    public long crearFactura(
            Map<String, Object> mock)
            throws BadRequestException {

        Map<String, Object> emisor =
            (Map<String, Object>) mock.get("emisor");

        Map<String, Object> comprobante =
            (Map<String, Object>) mock.get("comprobante");

        Map<String, Object> importes =
            (Map<String, Object>)
                comprobante.get("importes");

        Map<String, Object> periodoFacturado =
            (Map<String, Object>)
                comprobante.get("periodoFacturado");

        Map<String, Object> sucursalInmueble =
            (Map<String, Object>)
                mock.get("sucursalInmueble");


        Map<String, Object> body = new HashMap<>();

        body.put(
            "cuit",
            emisor.get("cuit")
        );

        body.put(
            "nis",
            sucursalInmueble.get("referencia")
        );

        body.put(
            "razonSocial",
            emisor.get("razonSocial")
        );

        body.put(
            "comprobante",
            comprobante.get("numero")
        );

        body.put(
            "observaciones",
            "TEST - Proveedor nuevo B0501"
        );

        body.put(
            "importe",
            importes.get("total")
        );

        String periodoDesde =
            (String) periodoFacturado.get("desde");

        body.put(
            "periodo",
            periodoDesde.substring(0, 7)
        );

        body.put(
            "fechaEmision",
            comprobante.get("fechaEmision")
        );

        return invoiceService.create(body);
    }
}