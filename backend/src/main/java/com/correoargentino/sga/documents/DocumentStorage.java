package com.correoargentino.sga.documents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Los binarios viven en el filesystem. La base sólo guarda ruta_relativa + sha256.
 */
@Component
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(@Value("${sga.storage.root:./storage}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public record Stored(UUID uuid, String rutaRelativa, String sha256, long size, String mime, String nombreOriginal) {}

    public Stored store(MultipartFile file) {
        try {
            UUID uuid = UUID.randomUUID();
            LocalDate now = LocalDate.now();
            String original = file.getOriginalFilename() == null ? "archivo" : Paths.get(file.getOriginalFilename()).getFileName().toString();
            String relative = String.format("%d/%02d/%s-%s", now.getYear(), now.getMonthValue(), uuid, original);
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            String sha = sha256(bytes);
            return new Stored(uuid, relative, sha, bytes.length,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(), original);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo almacenar el archivo: " + e.getMessage(), e);
        }
    }

    public InputStream read(String rutaRelativa) {
        try {
            Path target = root.resolve(rutaRelativa).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Ruta inválida");
            }
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage(), e);
        }
    }

    public void delete(String rutaRelativa) {
        try {
            Path target = root.resolve(rutaRelativa).normalize();

            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Ruta inválida");
            }

            Files.deleteIfExists(target);

        } catch (IOException e) {
            throw new RuntimeException(
                "No se pudo eliminar el archivo: " + e.getMessage(),
                e
            );
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}
