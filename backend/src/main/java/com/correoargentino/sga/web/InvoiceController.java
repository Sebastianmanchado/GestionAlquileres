package com.correoargentino.sga.web;

import com.correoargentino.sga.service.DocumentService;
import com.correoargentino.sga.service.InvoiceService;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping("/unassigned")
    public List<Map<String, Object>> unassigned() {
        return service.unassigned();
    }

    @GetMapping("/planificadas")
    public List<Map<String, Object>> planificadas() {
        return service.planificadas();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body) {

        try {
            long id = service.create(body);

            return ResponseEntity.ok(
                Map.of("id", id)
            );

        } catch (BadRequestException e) {

            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
                ));

        } catch (Exception e) {
            log.info(e.getMessage());
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Ocurrió un error interno."
                ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {

        try {
            service.update(id, body);

            return ResponseEntity.ok(
                Map.of(
                    "id", id,
                    "updated", true
                )
            );

        } catch (BadRequestException e) {

            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
                ));

        } catch (Exception e) {

            log.info(e.getMessage());

            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Ocurrió un error interno."
                ));
        }
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assign(
            @PathVariable long id,
            @RequestParam long contratoId) {

        try {
            service.assign(id, contratoId);

            return ResponseEntity.ok(
                Map.of(
                    "id", id,
                    "contratoId", contratoId
                )
            );

        } catch (BadRequestException e) {

            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
                ));

        } catch (NotFoundException e) {

            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "status", 404,
                    "error", "Not Found",
                    "message", e.getMessage()
                ));

        } catch (Exception e) {

            log.error(
                "Error al asignar factura {} al contrato {}",
                id,
                contratoId,
                e
            );

            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Ocurrió un error interno."
                ));
        }
    }
}
