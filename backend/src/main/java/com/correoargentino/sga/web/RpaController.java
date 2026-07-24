package com.correoargentino.sga.web;

import com.correoargentino.sga.service.RpaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rpa")
public class RpaController {

    private final RpaService service;

    public RpaController(RpaService service) {
        this.service = service;
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() {
        return service.runs();
    }
}
