package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RpaService {

    private final SgaRepository repo;
    private final ObjectMapper objectMapper;

    public RpaService(SgaRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
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
}
