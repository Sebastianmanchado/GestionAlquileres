package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {

    private final SgaRepository repo;
    private final CurrentUserProvider currentUser;

    public AuditService(SgaRepository repo, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    public void log(String entidad, String entidadId, String accion, String descripcion,
                    String contratoRef, String antes, String despues) {
        Long usuarioId = usuarioIdActual();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("entidad", entidad)
                .addValue("entidadId", entidadId)
                .addValue("accion", accion)
                .addValue("descripcion", descripcion)
                .addValue("contratoRef", contratoRef)
                .addValue("antes", antes)
                .addValue("despues", despues)
                .addValue("usuarioId", usuarioId);
        repo.jdbc().update("""
            INSERT INTO auditoria (entidad, entidad_id, accion, descripcion, contrato_ref, datos_antes, datos_despues, usuario_id)
            VALUES (:entidad, :entidadId, :accion, :descripcion, :contratoRef, :antes, :despues, :usuarioId)
            """, p);
    }

    private Long usuarioIdActual() {
        try {
            return repo.jdbc().queryForObject("SELECT id FROM usuario WHERE username=:u",
                    new MapSqlParameterSource("u", currentUser.currentUsername()), Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> audit(String desde, String hasta, String usuario, String rol, String contrato, String accion) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (desde != null && !desde.isBlank()) { where.append(" AND CAST(au.fecha AS DATE) >= :desde"); p.addValue("desde", desde); }
        if (hasta != null && !hasta.isBlank()) { where.append(" AND CAST(au.fecha AS DATE) <= :hasta"); p.addValue("hasta", hasta); }
        if (usuario != null && !usuario.isBlank()) { where.append(" AND u.nombre = :usuario"); p.addValue("usuario", usuario); }
        if (rol != null && !rol.isBlank()) { where.append(" AND rc.rol = :rol"); p.addValue("rol", rol); }
        if (contrato != null && !contrato.isBlank()) { where.append(" AND au.contrato_ref = :contrato"); p.addValue("contrato", contrato); }
        if (accion != null && !accion.isBlank()) { where.append(" AND au.descripcion = :accion"); p.addValue("accion", accion); }

        String sql = "SELECT au.fecha, u.nombre AS usuario, rc.rol AS rol, " +
                "au.contrato_ref AS contrato, au.descripcion AS accion " +
                "FROM auditoria au LEFT JOIN usuario u ON u.id=au.usuario_id " +
                "OUTER APPLY (SELECT TOP 1 rol FROM usuario_rol_cache WHERE usuario_id=u.id) rc " +
                "WHERE 1=1" + where + " ORDER BY au.fecha DESC";

        List<Map<String, Object>> rows = repo.query(sql, p);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("usuarios", distinct("SELECT DISTINCT u.nombre FROM auditoria au JOIN usuario u ON u.id=au.usuario_id ORDER BY u.nombre"));
        options.put("roles", distinct("SELECT DISTINCT rc.rol FROM auditoria au JOIN usuario u ON u.id=au.usuario_id JOIN usuario_rol_cache rc ON rc.usuario_id=u.id ORDER BY rc.rol"));
        options.put("contratos", distinct("SELECT DISTINCT contrato_ref FROM auditoria WHERE contrato_ref IS NOT NULL AND contrato_ref<>'—' ORDER BY contrato_ref"));
        options.put("acciones", distinct("SELECT DISTINCT descripcion FROM auditoria WHERE descripcion IS NOT NULL ORDER BY descripcion"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("options", options);
        return out;
    }

    private List<String> distinct(String sql) {
        return repo.jdbc().queryForList(sql, new MapSqlParameterSource(), String.class);
    }
}
