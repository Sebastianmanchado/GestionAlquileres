package com.correoargentino.sga.web;

import com.correoargentino.sga.repo.SgaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/catalogs")
public class CatalogController {

    private final SgaRepository repo;

    public CatalogController(SgaRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Map<String, Object> catalogs() {
        return repo.catalogs();
    }
}
