package com.correoargentino.sga.web;

import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.correoargentino.sga.service.NotificationService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestBody Map<String, Object> body)
            throws BadRequestException {

        long id = service.create(body);

        return Map.of(
            "id", id
        );
    }

    @GetMapping
    public Map<String, Object> get() {

        return Map.of(
            "notifications",
            service.get()
        );
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable long id)
            throws BadRequestException {

        service.delete(id);
    }
}
