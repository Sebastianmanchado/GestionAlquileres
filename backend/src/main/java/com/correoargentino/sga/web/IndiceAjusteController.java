package com.correoargentino.sga.web;


import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.service.ContractService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/api/indices")
public class IndiceAjusteController {

    private final SgaRepository repo;
    private final ContractService service;

    public IndiceAjusteController(
            SgaRepository repo,
            ContractService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "14") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir) {

        return repo.listIndices(search, page, size, sort, dir);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {

        Map<String, Object> detail = repo.getIndice(id);

        if (detail == null) {
            throw new NotFoundException("Índice no encontrado");
        }

        return detail;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body) {

        try {
            long id = service.createIndice(body);

            return ResponseEntity.ok(Map.of("id", id));

        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of(
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
}