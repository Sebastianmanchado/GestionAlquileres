package com.correoargentino.sga.web;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.service.ContractService;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inmuebles")
public class InmuebleController {

    private final SgaRepository repo;
    private final ContractService service;

    public InmuebleController(SgaRepository repo, ContractService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "14") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir) {
        return repo.listInmuebles(search, region, page, size, sort, dir);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> detail = repo.getInmueble(id);
        if (detail == null) throw new NotFoundException("Inmueble no encontrado");
        return detail;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            long id = service.createInmueble(body);
            return ResponseEntity.ok(Map.of("id", id));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Ocurrió un error interno."
            ));
        }
    }
}
