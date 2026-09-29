package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndiceSyncService {

    private static final Logger log = LoggerFactory.getLogger(IndiceSyncService.class);
    private static final int MESES_ICL = 24;
    private static final int PAGINA_ICL = 1000;

    private final SgaRepository repo;
    private final RestClient http;
    private final String ipcSeriesUrl;
    private final String iclBaseUrl;
    private final ZoneId zone;
    private final TransactionTemplate tx;

    public IndiceSyncService(
            SgaRepository repo,
            RestClient.Builder restClientBuilder,
            PlatformTransactionManager transactionManager,
            @Value("${sga.ajustes.ipc-series-url:https://api.argly.com.ar/v1/ipc?historico=true}") String ipcSeriesUrl,
            @Value("${sga.ajustes.icl-base-url:https://api.bcra.gob.ar/estadisticas/v4.0/monetarias/}") String iclBaseUrl,
            @Value("${sga.ajustes.zone:America/Argentina/Buenos_Aires}") String zone) {
        this.repo = repo;
        this.ipcSeriesUrl = ipcSeriesUrl;
        this.iclBaseUrl = iclBaseUrl;
        this.zone = ZoneId.of(zone);
        this.tx = new TransactionTemplate(transactionManager);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.http = restClientBuilder.requestFactory(factory).build();
    }

    public Map<String, Object> sincronizar() {
        int ipc = 0;
        int icl = 0;
        List<String> errores = new ArrayList<>();
        List<Map<String, Object>> indices = repo.query("""
                SELECT id, codigo, codigo_serie AS serie
                  FROM indice_ajuste
                 WHERE codigo IN (N'IPC', N'ICL')
                """, new MapSqlParameterSource());

        for (Map<String, Object> indice : indices) {
            String codigo = String.valueOf(indice.get("codigo"));
            String serie = indice.get("serie") == null ? "" : indice.get("serie").toString().trim();
            int id = ((Number) indice.get("id")).intValue();
            if (serie.isEmpty() || !serie.matches("[A-Za-z0-9._-]+")) {
                errores.add(codigo + ": no tiene una serie externa válida");
                continue;
            }
            try {
                int guardados = "ICL".equals(codigo) ? sincronizarIcl(id, serie) : sincronizarIpc(id);
                if ("ICL".equals(codigo)) icl = guardados;
                else ipc = guardados;
                if (guardados > 0) {
                    marcarSincronizado(id);
                }
                log.info("Índice {} sincronizado: {} niveles", codigo, guardados);
            } catch (RuntimeException e) {
                log.warn("No se pudo sincronizar {}: {}", codigo, e.getMessage());
                errores.add(codigo + ": " + (e.getMessage() == null ? "error de la API" : e.getMessage()));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ipc", ipc);
        out.put("icl", icl);
        out.put("errores", errores);
        out.put("ipcActualizadoEn", ipcActualizadoEnIso());
        return out;
    }

    public boolean ipcSincronizadoHoy() {
        LocalDateTime cuando = sincronizadoEn("IPC");
        if (cuando == null) return false;
        return !cuando.toLocalDate().isBefore(LocalDate.now(zone));
    }

    public String ipcActualizadoEnIso() {
        LocalDateTime cuando = sincronizadoEn("IPC");
        if (cuando == null) return null;
        return cuando.atZone(zone).toOffsetDateTime().toString();
    }

    private int sincronizarIpc(int indiceId) {
        JsonNode body = http.get()
                .uri(ipcSeriesUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "sga-alquileres")
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = body == null ? null : body.get("data");
        if (data == null || !data.isArray()) {
            throw new IllegalStateException("Argly no devolvió el histórico del IPC");
        }
        List<PuntoIpc> puntos = new ArrayList<>();
        for (JsonNode row : data) {
            int mes = row.path("mes").asInt(0);
            int anio = row.path("anio").asInt(0);
            if (mes < 1 || mes > 12 || anio < 1990) continue;
            if (row.path("valor").isMissingNode() || row.path("valor").isNull()) continue;
            BigDecimal variacion = new BigDecimal(row.path("valor").asText());
            if (variacion.compareTo(new BigDecimal("-100")) <= 0) continue;
            puntos.add(new PuntoIpc(LocalDate.of(anio, mes, 1), variacion));
        }
        if (puntos.isEmpty()) {
            throw new IllegalStateException("Argly no devolvió variaciones de IPC");
        }
        Integer guardados = tx.execute(status -> {
            repo.jdbc().update("""
                    DELETE FROM indice_valor
                     WHERE indice_id = :indiceId AND origen = N'API'
                    """, new MapSqlParameterSource("indiceId", indiceId));
            for (PuntoIpc punto : puntos) {
                upsert(indiceId, punto.periodo(), punto.variacion());
            }
            return puntos.size();
        });
        return guardados == null ? 0 : guardados;
    }

    private record PuntoIpc(LocalDate periodo, BigDecimal variacion) {
    }

    private int sincronizarIcl(int indiceId, String variableId) {
        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusMonths(MESES_ICL);
        int offset = 0;
        int guardados = 0;
        int total = Integer.MAX_VALUE;
        while (offset < total) {
            String url = iclBaseUrl + variableId
                    + "?desde=" + desde
                    + "&hasta=" + hasta
                    + "&limit=" + PAGINA_ICL
                    + "&offset=" + offset;
            JsonNode body = http.get()
                    .uri(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "sga-alquileres")
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) break;
            total = body.path("metadata").path("resultset").path("count").asInt(0);
            JsonNode detalle = body.path("results").path(0).path("detalle");
            if (!detalle.isArray() || detalle.isEmpty()) break;
            for (JsonNode row : detalle) {
                if (row.path("valor").isMissingNode() || row.path("valor").isNull()) continue;
                LocalDate periodo = LocalDate.parse(row.path("fecha").asText().substring(0, 10));
                BigDecimal valor = new BigDecimal(row.path("valor").asText());
                if (valor.signum() <= 0) continue;
                upsert(indiceId, periodo, valor);
                guardados++;
            }
            offset += detalle.size();
            if (detalle.size() < PAGINA_ICL) break;
        }
        return guardados;
    }

    private void marcarSincronizado(int indiceId) {
        repo.jdbc().update("""
                UPDATE indice_ajuste
                   SET sincronizado_en = :cuando
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", indiceId)
                .addValue("cuando", LocalDateTime.now(zone)));
    }

    private LocalDateTime sincronizadoEn(String codigo) {
        Map<String, Object> row = repo.queryOne("""
                SELECT sincronizado_en AS sincronizadoEn
                  FROM indice_ajuste
                 WHERE codigo = :codigo
                """, new MapSqlParameterSource("codigo", codigo));
        if (row == null || row.get("sincronizadoEn") == null) return null;
        Object raw = row.get("sincronizadoEn");
        if (raw instanceof LocalDateTime fecha) return fecha;
        if (raw instanceof LocalDate fecha) return fecha.atStartOfDay();
        return null;
    }

    private void upsert(int indiceId, LocalDate periodo, BigDecimal valor) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indiceId", indiceId)
                .addValue("periodo", periodo)
                .addValue("valor", valor);
        int updated = repo.jdbc().update("""
                UPDATE indice_valor
                   SET valor = :valor, origen = N'API'
                 WHERE indice_id = :indiceId AND periodo = :periodo
                """, params);
        if (updated == 0) {
            repo.jdbc().update("""
                    INSERT INTO indice_valor (indice_id, periodo, valor, origen)
                    VALUES (:indiceId, :periodo, :valor, N'API')
                    """, params);
        }
    }
}
