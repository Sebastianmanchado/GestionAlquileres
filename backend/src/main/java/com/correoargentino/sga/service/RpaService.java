package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.coyote.BadRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RpaService {

    private final SgaRepository repo;
    private final ObjectMapper objectMapper;
    private final InvoiceService invoiceService;

    public RpaService(SgaRepository repo, ObjectMapper objectMapper, InvoiceService invoiceService) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.invoiceService = invoiceService;
    }

    public List<Map<String, Object>> runs() {
        List<Map<String, Object>> rows = repo.query("""
            SELECT id, fecha_ejecucion AS fecha, periodo_desde AS periodoDesde, periodo_hasta AS periodoHasta,
                   estado AS estadoCodigo, facturas_recibidas AS recibidas, facturas_procesadas AS procesadas,
                   facturas_con_error AS conError, payload_crudo AS payload
              FROM rpa_ejecucion ORDER BY fecha_ejecucion DESC
            """, new MapSqlParameterSource());
        for (Map<String, Object> r : rows) {
            Object payload = r.remove("payload");
            List<String> errores = new ArrayList<>();
            if (payload != null) {
                try {
                    String[] arr = objectMapper.readValue(payload.toString(), String[].class);
                    errores.addAll(List.of(arr));
                } catch (Exception ignored) {
                    // payload no es un arreglo de errores
                }
            }
            r.put("errores", errores);
        }
        return rows;
    }

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
