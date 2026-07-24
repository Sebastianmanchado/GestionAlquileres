package com.correoargentino.sga.service;

import com.correoargentino.sga.documents.DocumentStorage;
import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class DocumentService {

    private final SgaRepository repo;
    private final DocumentStorage storage;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public DocumentService(SgaRepository repo, DocumentStorage storage, AuditService audit, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.storage = storage;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public Map<String, Object> upload(long contratoId, MultipartFile file, String tipoDocumento) {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no puede subir documentos.");
        }
        DocumentStorage.Stored stored = storage.store(file);
        KeyHolder kh = new GeneratedKeyHolder();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("uuid", stored.uuid().toString())
                .addValue("nombre", stored.nombreOriginal())
                .addValue("ruta", stored.rutaRelativa())
                .addValue("mime", stored.mime())
                .addValue("size", stored.size())
                .addValue("sha", stored.sha256());
        repo.jdbc().update("""
            INSERT INTO archivo (uuid, nombre_original, ruta_relativa, mime_type, tamano_bytes, sha256, storage_backend)
            VALUES (:uuid, :nombre, :ruta, :mime, :size, :sha, 'FILESYSTEM')
            """, p, kh, new String[]{"id"});
        long archivoId = kh.getKey().longValue();

        repo.jdbc().update("""
            INSERT INTO contrato_archivo (contrato_id, archivo_id, tipo_documento)
            VALUES (:c, :a, :tipo)
            """, new MapSqlParameterSource().addValue("c", contratoId).addValue("a", archivoId)
                .addValue("tipo", tipoDocumento == null ? "Documento" : tipoDocumento));

        audit.log("contrato", String.valueOf(contratoId), "ADJUNTAR", "Adjuntó documento: " + stored.nombreOriginal(), null, null, null);

        return Map.of("archivoId", archivoId, "nombre", stored.nombreOriginal(), "tipo",
                tipoDocumento == null ? "Documento" : tipoDocumento);
    }

    public Map<String, Object> archivo(long archivoId) {
        Map<String, Object> a = repo.queryOne("SELECT id, nombre_original AS nombre, ruta_relativa AS ruta, mime_type AS mime FROM archivo WHERE id=:id",
                new MapSqlParameterSource("id", archivoId));
        if (a == null) throw new NotFoundException("Archivo no encontrado");
        return a;
    }

    public java.io.InputStream read(String ruta) {
        return storage.read(ruta);
    }
}
