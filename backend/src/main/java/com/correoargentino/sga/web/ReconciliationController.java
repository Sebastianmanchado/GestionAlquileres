package com.correoargentino.sga.web;

import com.correoargentino.sga.service.ReconciliationService;
import com.correoargentino.sga.service.SapAsientoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {

    private final ReconciliationService service;
    private final SapAsientoService sap;

    public ReconciliationController(ReconciliationService service, SapAsientoService sap) {
        this.service = service;
        this.sap = sap;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String periodo) {
        return service.list(periodo);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping("/sap")
    public Map<String, Object> enviarSap(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Indicá al menos una conciliación.");
        }
        List<Long> ids = list.stream()
                .map(value -> Long.parseLong(value.toString()))
                .toList();
        return Map.of("resultados", sap.enviar(ids));
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestParam(required = false) String periodo) {
        int procesadas = service.run(periodo);
        return Map.of("procesadas", procesadas);
    }

    @PostMapping("/{id}/action")
    public Map<String, Object> action(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String action = body.get("action") == null ? null : body.get("action").toString();
        String comentario = body.get("comentario") == null ? null : body.get("comentario").toString();
        service.action(id, action, comentario);
        return Map.of("id", id, "action", action == null ? "" : action);
    }
}
