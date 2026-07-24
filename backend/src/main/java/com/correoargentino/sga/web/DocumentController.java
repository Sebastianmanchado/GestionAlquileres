package com.correoargentino.sga.web;

import com.correoargentino.sga.service.DocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam long contratoId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String tipoDocumento) {
        return service.upload(contratoId, file, tipoDocumento);
    }

    @GetMapping("/{archivoId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long archivoId) {
        Map<String, Object> a = service.archivo(archivoId);
        var stream = service.read(a.get("ruta").toString());
        String nombre = String.valueOf(a.get("nombre"));
        String mime = a.get("mime") == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : a.get("mime").toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(new InputStreamResource(stream));
    }
}
