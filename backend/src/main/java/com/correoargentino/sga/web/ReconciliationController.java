package com.correoargentino.sga.web;

import com.correoargentino.sga.service.ReconciliationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String periodo) {
        return service.list(periodo);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return service.detail(id);
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
