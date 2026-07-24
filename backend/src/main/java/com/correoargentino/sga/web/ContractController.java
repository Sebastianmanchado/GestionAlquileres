package com.correoargentino.sga.web;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.service.ContractService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final SgaRepository repo;
    private final ContractService service;

    public ContractController(SgaRepository repo, ContractService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String indice,
            @RequestParam(required = false) String venc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "14") int size) {
        return repo.listContracts(search, region, estado, indice, venc, page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> detail = repo.getContractDetail(id);
        if (detail == null) throw new NotFoundException("Contrato no encontrado");
        return detail;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long id = service.create(body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return out;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.update(id, body);
        return Map.of("id", id, "updated", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        service.softDelete(id);
        return Map.of("id", id, "deleted", true);
    }
}
