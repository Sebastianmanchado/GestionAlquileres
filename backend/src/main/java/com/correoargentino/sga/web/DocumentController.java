package com.correoargentino.sga.web;

import com.correoargentino.sga.service.DocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("facturaId") Integer facturaId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String tipoDocumento) {
        
        return service.upload(facturaId, file, tipoDocumento);
    }

    @GetMapping()
    public List<Map<String, Object>> getDocuments() {
        List<Map<String, Object>> a = service.getArchivos();
        return a;
    }

    @GetMapping("/factura/{facturaId}")
    public ResponseEntity<InputStreamResource> getByFacturaId(
            @PathVariable long facturaId) {

        Map<String, Object> archivo = service.archivoPorFactura(facturaId);

        InputStream stream = service.read(
            archivo.get("ruta_relativa").toString()
        );

        String nombre = String.valueOf(archivo.get("nombre_original"));

        String mime = archivo.get("mime_type") == null
            ? MediaType.APPLICATION_PDF_VALUE
            : archivo.get("mime_type").toString();

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + nombre + "\""
            )
            .contentType(MediaType.parseMediaType(mime))
            .body(new InputStreamResource(stream));
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

    @DeleteMapping("/factura/{facturaId}")
        public ResponseEntity<Void> deleteByFacturaId(
                @PathVariable long facturaId) {

            service.eliminarPorFactura(facturaId);

            return ResponseEntity.noContent().build();
        }
}
