package com.correoargentino.sga.service;

import java.util.List;
import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.correoargentino.sga.repo.SgaRepository;

@Service
public class NotificationService {

    private final SgaRepository repo;

    public NotificationService(SgaRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public long create(Map<String, Object> body)
            throws BadRequestException {

        String texto = requireString(body, "texto");

        String tiempo = body.get("tiempo") != null
            ? String.valueOf(body.get("tiempo"))
            : null;

        KeyHolder kh = new GeneratedKeyHolder();

        repo.jdbc().update(
            """
            INSERT INTO notificacion (
                texto,
                tiempo
            )
            VALUES (
                :texto,
                :tiempo
            )
            """,
            new MapSqlParameterSource()
                .addValue("texto", texto)
                .addValue("tiempo", tiempo),
            kh,
            new String[]{"id"}
        );

        return kh.getKey().longValue();
    }

    public List<Map<String, Object>> get() {

        return repo.query(
            """
            SELECT
                id,
                texto,
                tiempo,
                creado_en AS creadoEn
            FROM notificacion
            ORDER BY creado_en DESC, id DESC
            """,
            new MapSqlParameterSource()
        );
    }

    @Transactional
    public void delete(long id)
            throws BadRequestException {

        int updated = repo.jdbc().update(
            """
            DELETE FROM notificacion
            WHERE id = :id
            """,
            new MapSqlParameterSource()
                .addValue("id", id)
        );

        if (updated == 0) {
            throw new BadRequestException(
                "No se encontró la notificación."
            );
        }
    }

    private String requireString(
            Map<String, Object> body,
            String key)
            throws BadRequestException {

        Object value = body.get(key);

        if (value == null ||
            value.toString().trim().isEmpty()) {

            throw new BadRequestException(
                "El campo '" + key + "' es obligatorio."
            );
        }

        return value.toString().trim();
    }
}