package com.correoargentino.sga.service;

import com.correoargentino.sga.documents.DocumentStorage;
import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentService {

    private final SgaRepository repo;
    private final DocumentStorage storage;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    public DocumentService(SgaRepository repo, DocumentStorage storage, AuditService audit, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.storage = storage;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public Map<String, Object> upload(
            Integer facturaId,
            MultipartFile file,
            String tipoDocumento) {

        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException(
                "El rol actual no puede subir documentos."
            );
        }

        MapSqlParameterSource params =
            new MapSqlParameterSource()
                .addValue("facturaId", facturaId);

        List<Map<String, Object>> existentes = repo.jdbc().queryForList(
            """
            SELECT
                id,
                ruta_relativa
            FROM archivo
            WHERE factura_id = :facturaId
            """,
            params
        );

        DocumentStorage.Stored stored = storage.store(file);

        long archivoId;

        if (!existentes.isEmpty()) {

            Map<String, Object> existente = existentes.get(0);

            archivoId = ((Number) existente.get("id")).longValue();

            String rutaAnterior =
                String.valueOf(existente.get("ruta_relativa"));

            storage.delete(rutaAnterior);

            repo.jdbc().update(
                """
                UPDATE archivo
                SET
                    uuid = :uuid,
                    nombre_original = :nombre,
                    ruta_relativa = :ruta,
                    mime_type = :mime,
                    tamano_bytes = :size,
                    sha256 = :sha,
                    storage_backend = 'FILESYSTEM'
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                    .addValue("id", archivoId)
                    .addValue("uuid", stored.uuid().toString())
                    .addValue("nombre", stored.nombreOriginal())
                    .addValue("ruta", stored.rutaRelativa())
                    .addValue("mime", stored.mime())
                    .addValue("size", stored.size())
                    .addValue("sha", stored.sha256())
            );

            audit.log(
                "archivo",
                String.valueOf(archivoId),
                "REEMPLAZAR",
                "Reemplazó documento: " + stored.nombreOriginal(),
                null,
                null,
                null
            );

        } else {

            KeyHolder kh = new GeneratedKeyHolder();

            repo.jdbc().update(
                """
                INSERT INTO archivo (
                    uuid,
                    factura_id,
                    nombre_original,
                    ruta_relativa,
                    mime_type,
                    tamano_bytes,
                    sha256,
                    storage_backend
                )
                VALUES (
                    :uuid,
                    :facturaId,
                    :nombre,
                    :ruta,
                    :mime,
                    :size,
                    :sha,
                    'FILESYSTEM'
                )
                """,
                new MapSqlParameterSource()
                    .addValue("uuid", stored.uuid().toString())
                    .addValue("facturaId", facturaId)
                    .addValue("nombre", stored.nombreOriginal())
                    .addValue("ruta", stored.rutaRelativa())
                    .addValue("mime", stored.mime())
                    .addValue("size", stored.size())
                    .addValue("sha", stored.sha256()),
                kh,
                new String[]{"id"}
            );

            archivoId = kh.getKey().longValue();

            audit.log(
                "archivo",
                String.valueOf(archivoId),
                "ADJUNTAR",
                "Adjuntó documento: " + stored.nombreOriginal(),
                null,
                null,
                null
            );
        }

        return Map.of(
            "archivoId", archivoId,
            "nombre", stored.nombreOriginal(),
            "tipo",
            tipoDocumento == null
                ? "Documento"
                : tipoDocumento
        );
    }

    public Map<String, Object> archivo(long archivoId) {
        Map<String, Object> a = repo.queryOne("SELECT id, nombre_original AS nombre, ruta_relativa AS ruta, mime_type AS mime FROM archivo WHERE id=:id",
                new MapSqlParameterSource("id", archivoId));
        if (a == null) throw new NotFoundException("Archivo no encontrado");
        return a;
    }

    public List<Map<String, Object>> getArchivos() {

        List<Map<String, Object>> archivos = repo.query("""
            SELECT
                *
            FROM archivo
            """,
            new MapSqlParameterSource()
        );

        return archivos;
    }

    public Map<String, Object> archivoPorFactura(long facturaId) {

        String sql = """
            SELECT
                ruta_relativa,
                nombre_original,
                mime_type
            FROM archivo
            WHERE factura_id = :facturaId
            ORDER BY creado_en DESC
            """;

        MapSqlParameterSource params =
            new MapSqlParameterSource()
                .addValue("facturaId", facturaId);

        List<Map<String, Object>> archivos =
            repo.jdbc().queryForList(sql, params);

        if (archivos.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe un archivo asociado a la factura " + facturaId
            );
        }

        return archivos.get(0);
    }

    @Transactional
    public void eliminarPorFactura(long facturaId) {

        List<String> rutas = repo.jdbc().query(
            """
            SELECT ruta_relativa
            FROM archivo
            WHERE factura_id = :facturaId
            """,
            Map.of("facturaId", facturaId),
            (rs, rowNum) -> rs.getString("ruta_relativa")
        );

        for (String ruta : rutas) {
            storage.delete(ruta);
        }

        repo.jdbc().update(
            "DELETE FROM archivo WHERE factura_id = :facturaId",
            Map.of("facturaId", facturaId)
        );
    }

    public java.io.InputStream read(String ruta) {
        return storage.read(ruta);
    }
}
