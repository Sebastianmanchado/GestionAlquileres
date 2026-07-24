package com.correoargentino.sga.web;

import com.correoargentino.sga.service.InvoiceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping("/unassigned")
    public List<Map<String, Object>> unassigned() {
        return service.unassigned();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long id = service.create(body);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.update(id, body);
        return Map.of("id", id, "updated", true);
    }

    @PostMapping("/{id}/assign")
    public Map<String, Object> assign(@PathVariable long id, @RequestParam long contratoId) {
        service.assign(id, contratoId);
        return Map.of("id", id, "contratoId", contratoId);
    }
}
