package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class AjusteAutomaticoService {

    private static final Logger log = LoggerFactory.getLogger(AjusteAutomaticoService.class);
    private static final int MAX_PERIODOS = 36;

    private final SgaRepository repo;
    private final ContractService contracts;
    private final IndiceSyncService sync;
    private final CurrentUserProvider currentUser;

    public AjusteAutomaticoService(
            SgaRepository repo,
            ContractService contracts,
            IndiceSyncService sync,
            CurrentUserProvider currentUser) {
        this.repo = repo;
        this.contracts = contracts;
        this.sync = sync;
        this.currentUser = currentUser;
    }

    public Map<String, Object> ejecutarManual() {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no tiene permiso para modificar contratos.");
        }
        return ejecutar();
    }

    public Map<String, Object> ejecutar() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.putAll(sync.sincronizar());
        } catch (RuntimeException e) {
            log.warn("La sincronización de índices falló: {}", e.getMessage());
            out.put("ipc", 0);
            out.put("icl", 0);
            out.put("errores", List.of(e.getMessage() == null ? "error al sincronizar" : e.getMessage()));
        }
        Map<String, Object> aplicados = aplicarPendientes();
        out.putAll(aplicados);
        log.info("Ajustes automáticos: aplicados={}, omitidos={}, errores={}",
                aplicados.get("aplicados"), aplicados.get("omitidos"), aplicados.get("erroresAplicacion"));
        return out;
    }

    public Map<String, Object> proximoAjuste(long contratoId) {
        Map<String, Object> contrato = repo.queryOne("""
                SELECT c.fecha_vencimiento AS vencimiento,
                       c.periodicidad_ajuste AS periodicidad,
                       ia.codigo AS indiceCodigo,
                       ec.codigo AS estadoCodigo,
                       cv.vigencia_desde AS desde
                  FROM contrato c
                  JOIN estado_contrato ec ON ec.id = c.estado_contrato_id
                  LEFT JOIN indice_ajuste ia ON ia.id = c.indice_ajuste_id
                  OUTER APPLY (
                      SELECT TOP 1 vigencia_desde
                        FROM contrato_valor
                       WHERE contrato_id = c.id AND vigencia_hasta IS NULL
                       ORDER BY vigencia_desde DESC
                  ) cv
                 WHERE c.id = :id
                """, new MapSqlParameterSource("id", contratoId));
        if (contrato == null) return null;
        if (!"VIGENTE".equals(str(contrato.get("estadoCodigo")))) return null;
        String codigo = str(contrato.get("indiceCodigo"));
        if (!"IPC".equals(codigo) && !"ICL".equals(codigo)) return null;
        int meses = AjusteIndiceCalculo.meses(str(contrato.get("periodicidad")));
        if (meses == 0 || contrato.get("desde") == null || contrato.get("vencimiento") == null) return null;

        LocalDate base = AjusteIndiceCalculo.toLocalDate(contrato.get("desde"));
        LocalDate vencimiento = AjusteIndiceCalculo.toLocalDate(contrato.get("vencimiento"));
        if (base == null || vencimiento == null) return null;
        NavigableMap<LocalDate, BigDecimal> niveles = niveles(codigo);
        return estadoProximo(codigo, meses, base, vencimiento, niveles, LocalDate.now());
    }

    Map<String, Object> aplicarPendientes() {
        LocalDate hoy = LocalDate.now();
        NavigableMap<LocalDate, BigDecimal> ipc = niveles("IPC");
        NavigableMap<LocalDate, BigDecimal> icl = niveles("ICL");
        List<Map<String, Object>> contratos = repo.query("""
                SELECT c.id, c.fecha_vencimiento AS vencimiento,
                       c.periodicidad_ajuste AS periodicidad,
                       c.indice_ajuste_id AS indiceId,
                       ia.codigo AS indiceCodigo,
                       i.nis,
                       cv.vigencia_desde AS desde,
                       cv.importe_mensual AS importe
                  FROM contrato c
                  JOIN estado_contrato ec ON ec.id = c.estado_contrato_id
                  JOIN indice_ajuste ia ON ia.id = c.indice_ajuste_id
                  JOIN inmueble i ON i.id = c.inmueble_id
                  OUTER APPLY (
                      SELECT TOP 1 vigencia_desde, importe_mensual
                        FROM contrato_valor
                       WHERE contrato_id = c.id AND vigencia_hasta IS NULL
                       ORDER BY vigencia_desde DESC
                  ) cv
                 WHERE ec.codigo = N'VIGENTE'
                   AND ia.codigo IN (N'IPC', N'ICL')
                   AND c.periodicidad_ajuste IN (N'TRIMESTRAL', N'CUATRIMESTRAL', N'SEMESTRAL')
                   AND cv.vigencia_desde IS NOT NULL
                """, new MapSqlParameterSource());

        int aplicados = 0;
        int omitidos = 0;
        List<String> errores = new ArrayList<>();
        for (Map<String, Object> contrato : contratos) {
            long id = ((Number) contrato.get("id")).longValue();
            try {
                String codigo = str(contrato.get("indiceCodigo"));
                NavigableMap<LocalDate, BigDecimal> niveles = "ICL".equals(codigo) ? icl : ipc;
                int n = aplicarContrato(contrato, niveles, hoy);
                if (n == 0) omitidos++;
                else aplicados += n;
            } catch (RuntimeException e) {
                log.warn("No se pudo ajustar el contrato {}: {}", id, e.getMessage());
                errores.add(id + ": " + (e.getMessage() == null ? "error" : e.getMessage()));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aplicados", aplicados);
        out.put("omitidos", omitidos);
        out.put("erroresAplicacion", errores);
        return out;
    }

    private int aplicarContrato(Map<String, Object> contrato, NavigableMap<LocalDate, BigDecimal> niveles, LocalDate hoy) {
        int meses = AjusteIndiceCalculo.meses(str(contrato.get("periodicidad")));
        LocalDate vencimiento = AjusteIndiceCalculo.toLocalDate(contrato.get("vencimiento"));
        String codigo = str(contrato.get("indiceCodigo"));
        long contratoId = ((Number) contrato.get("id")).longValue();
        int indiceId = ((Number) contrato.get("indiceId")).intValue();
        String nis = str(contrato.get("nis"));
        if (meses == 0 || vencimiento == null) return 0;

        int aplicados = 0;
        LocalDate base = AjusteIndiceCalculo.toLocalDate(contrato.get("desde"));
        BigDecimal importe = toDecimal(contrato.get("importe"));
        for (int i = 0; i < MAX_PERIODOS; i++) {
            if (base == null || importe == null) break;
            LocalDate proxima = base.plusMonths(meses);
            if (proxima.isAfter(hoy) || proxima.isAfter(vencimiento)) break;
            BigDecimal coef = coeficiente(codigo, niveles, base, proxima);
            if (coef == null) break;
            BigDecimal nuevo = AjusteIndiceCalculo.importeAjustado(importe, coef);
            boolean escrito = contracts.aplicarAjusteIndiceSistema(
                    contratoId, proxima, nuevo, indiceId, coef, nis, importe.toPlainString());
            if (!escrito) break;
            base = proxima;
            importe = nuevo;
            aplicados++;
        }
        return aplicados;
    }

    private Map<String, Object> estadoProximo(
            String codigo,
            int meses,
            LocalDate base,
            LocalDate vencimiento,
            NavigableMap<LocalDate, BigDecimal> niveles,
            LocalDate hoy) {
        LocalDate proxima = base.plusMonths(meses);
        if (proxima.isAfter(vencimiento)) return null;
        if (proxima.isAfter(hoy)) return Map.of("fecha", proxima.toString(), "estado", "PROGRAMADO");
        if (coeficiente(codigo, niveles, base, proxima) == null) {
            return Map.of("fecha", proxima.toString(), "estado", "ESPERANDO_INDICE");
        }
        return Map.of("fecha", proxima.toString(), "estado", "PENDIENTE");
    }

    private BigDecimal coeficiente(
            String codigo,
            NavigableMap<LocalDate, BigDecimal> niveles,
            LocalDate base,
            LocalDate proxima) {
        if ("ICL".equals(codigo)) {
            return AjusteIndiceCalculo.coeficiente(
                    AjusteIndiceCalculo.nivelEnFechaOAnterior(niveles, proxima, AjusteIndiceCalculo.DIAS_TOLERANCIA_ICL),
                    AjusteIndiceCalculo.nivelEnFechaOAnterior(niveles, base, AjusteIndiceCalculo.DIAS_TOLERANCIA_ICL));
        }
        return AjusteIndiceCalculo.coeficiente(
                niveles.get(AjusteIndiceCalculo.mesIndiceIpc(proxima)),
                niveles.get(AjusteIndiceCalculo.mesIndiceIpc(base)));
    }

    private NavigableMap<LocalDate, BigDecimal> niveles(String codigo) {
        List<Map<String, Object>> rows = repo.query("""
                SELECT iv.periodo, iv.valor
                  FROM indice_valor iv
                  JOIN indice_ajuste ia ON ia.id = iv.indice_id
                 WHERE ia.codigo = :codigo AND iv.origen = N'API'
                """, new MapSqlParameterSource("codigo", codigo));
        NavigableMap<LocalDate, BigDecimal> niveles = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate periodo = AjusteIndiceCalculo.toLocalDate(row.get("periodo"));
            BigDecimal valor = toDecimal(row.get("valor"));
            if (periodo != null && valor != null) niveles.put(periodo, valor);
        }
        return niveles;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
