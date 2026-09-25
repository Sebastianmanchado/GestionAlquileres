package com.correoargentino.sga.web;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.service.AjusteAutomaticoService;
import com.correoargentino.sga.service.ContractService;
import com.correoargentino.sga.service.InvoiceService;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final SgaRepository repo;
    private final ContractService service;
    private final AjusteAutomaticoService ajustes;
    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    public ContractController(SgaRepository repo, ContractService service, AjusteAutomaticoService ajustes) {
        this.repo = repo;
        this.service = service;
        this.ajustes = ajustes;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String indice,
            @RequestParam(required = false) String venc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "14") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir) {
        return repo.listContracts(search, region, estado, indice, venc, page, size, sort, dir);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> detail = repo.getContractDetail(id);
        if (detail == null) throw new NotFoundException("Contrato no encontrado");
        detail.put("proximoAjuste", ajustes.proximoAjuste(id));
        return detail;
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

        } catch (NotFoundException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "status", 404,
                    "error", "Not Found",
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


    @PostMapping("/{id}/ajustes")
    public ResponseEntity<Map<String, Object>> registrarAjuste(
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        try {
            service.registrarAjuste(id, body);
            return ResponseEntity.ok(Map.of("id", id, "ajustado", true));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "error", "Not Found",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Ocurrió un error interno."
            ));
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        service.softDelete(id);
        return Map.of("id", id, "deleted", true);
    }
}
