package com.correoargentino.sga.web;

import com.correoargentino.sga.service.AuditService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> audit(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String contrato,
            @RequestParam(required = false) String accion) {
        return service.audit(desde, hasta, usuario, rol, contrato, accion);
    }
}
