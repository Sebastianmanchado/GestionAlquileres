package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final SgaRepository repo;

    public DashboardService(SgaRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> dashboard() {
        var empty = new MapSqlParameterSource();
        Map<String, Object> out = new LinkedHashMap<>();

        int vigentes = intOf("SELECT COUNT(*) FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id WHERE e.codigo='VIGENTE'");
        int prox = intOf("SELECT COUNT(*) FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id WHERE e.codigo='PROX_VENCER'");
        int vencidos = intOf("SELECT COUNT(*) FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id WHERE e.codigo='VENCIDO'");
        int totales = intOf("SELECT COUNT(*) FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id WHERE e.codigo<>'RESCINDIDO'");
        int vencen90 = intOf("SELECT COUNT(*) FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id " +
                "WHERE e.codigo IN ('VIGENTE','PROX_VENCER') AND c.fecha_vencimiento BETWEEN CAST(GETDATE() AS DATE) AND DATEADD(DAY,90,CAST(GETDATE() AS DATE))");

        Map<String, Object> lastConc = repo.queryOne(
                "SELECT TOP 1 periodo FROM conciliacion ORDER BY periodo DESC", empty);
        Object periodo = lastConc == null ? null : lastConc.get("periodo");
        MapSqlParameterSource pp = new MapSqlParameterSource("periodo", periodo);
        int concDiff = periodo == null ? 0 : intOf("SELECT COUNT(*) FROM conciliacion WHERE periodo=:periodo AND estado='CON_DIFERENCIA'", pp);
        int concProcesadas = periodo == null ? 0 : intOf("SELECT COUNT(*) FROM conciliacion WHERE periodo=:periodo", pp);

        int sinAsignar = intOf("SELECT COUNT(*) FROM factura WHERE estado='SIN_ASIGNAR'");
        BigDecimal monto = decimalOf("SELECT ISNULL(SUM(cv.importe_mensual),0) FROM contrato c " +
                "JOIN estado_contrato e ON e.id=c.estado_contrato_id " +
                "JOIN contrato_valor cv ON cv.contrato_id=c.id AND cv.vigencia_hasta IS NULL " +
                "WHERE e.codigo IN ('VIGENTE','PROX_VENCER')");

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("contratosVigentes", vigentes);
        kpi.put("contratosVencidos", vencidos);
        kpi.put("contratosProximos", prox);
        kpi.put("contratosTotales", totales);
        kpi.put("vencen90", vencen90);
        kpi.put("carteraPct", totales == 0 ? 0 : Math.round(vencen90 * 1000.0 / totales) / 10.0);
        kpi.put("concConDiferencia", concDiff);
        kpi.put("concProcesadas", concProcesadas);
        kpi.put("periodo", periodo);
        kpi.put("facturasSinAsignar", sinAsignar);
        kpi.put("montoMensual", monto);
        out.put("kpi", kpi);

        // Evolución del gasto mensual (real, según facturas por período)
        List<Map<String, Object>> bars = repo.query("""
            SELECT TOP 8 periodo_facturado AS periodo, SUM(importe_total) AS total
              FROM factura
             WHERE periodo_facturado IS NOT NULL
             GROUP BY periodo_facturado
             ORDER BY periodo_facturado DESC
            """, empty);
        // devolver en orden ascendente
        List<Map<String, Object>> chart = new ArrayList<>(bars);
        java.util.Collections.reverse(chart);
        out.put("chart", chart);

        // Requiere tu atención (derivado de datos reales)
        List<String> attention = new ArrayList<>();
        int vencenSemana = intOf("SELECT COUNT(*) FROM contrato c JOIN estado_contrato e ON e.id=c.estado_contrato_id " +
                "WHERE e.codigo IN ('VIGENTE','PROX_VENCER') AND c.fecha_vencimiento BETWEEN CAST(GETDATE() AS DATE) AND DATEADD(DAY,7,CAST(GETDATE() AS DATE))");
        if (vencenSemana > 0) attention.add(vencenSemana + " contratos vencen esta semana y requieren renovación.");
        if (concDiff > 0) attention.add(concDiff + " conciliaciones del período con diferencia de importe.");
        int sinAsignarViejas = intOf("SELECT COUNT(*) FROM factura WHERE estado='SIN_ASIGNAR' AND fecha_emision < DATEADD(DAY,-15,CAST(GETDATE() AS DATE))");
        if (sinAsignarViejas > 0) attention.add(sinAsignarViejas + " facturas siguen sin asignar hace más de 15 días.");
        Map<String, Object> lastErr = repo.queryOne("SELECT TOP 1 fecha_ejecucion AS fecha, facturas_con_error AS err FROM rpa_ejecucion WHERE estado='CON_ERRORES' ORDER BY fecha_ejecucion DESC", empty);
        if (lastErr != null) attention.add("La última corrida RPA finalizó con " + lastErr.get("err") + " errores de lectura.");
        out.put("attention", attention);

        // Notificaciones
        List<Map<String, Object>> notifs = new ArrayList<>();
        Map<String, Object> proxContrato = repo.queryOne("SELECT TOP 1 i.nis, c.fecha_vencimiento AS venc FROM contrato c JOIN inmueble i ON i.id=c.inmueble_id " +
                "JOIN estado_contrato e ON e.id=c.estado_contrato_id WHERE e.codigo IN ('VIGENTE','PROX_VENCER') AND c.fecha_vencimiento >= CAST(GETDATE() AS DATE) ORDER BY c.fecha_vencimiento", empty);
        if (proxContrato != null) notifs.add(notif("Contrato " + proxContrato.get("nis") + " próximo a vencer.", "Vencimiento " + proxContrato.get("venc")));
        Map<String, Object> unaFactura = repo.queryOne("SELECT TOP 1 cuit_emisor AS cuit FROM factura WHERE estado='SIN_ASIGNAR' ORDER BY id DESC", empty);
        if (unaFactura != null) notifs.add(notif("Factura sin asignar: CUIT " + unaFactura.get("cuit") + ".", "Bandeja de conciliación"));
        if (concDiff > 0) notifs.add(notif(concDiff + " diferencias detectadas en el período.", "Conciliación"));
        if (lastErr != null) notifs.add(notif("Corrida RPA completada con " + lastErr.get("err") + " errores.", "Monitor RPA"));
        out.put("notifications", notifs);

        return out;
    }

    private Map<String, Object> notif(String texto, String tiempo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texto", texto);
        m.put("tiempo", tiempo);
        return m;
    }

    private int intOf(String sql) {
        return intOf(sql, new MapSqlParameterSource());
    }

    private int intOf(String sql, MapSqlParameterSource p) {
        Integer v = repo.jdbc().queryForObject(sql, p, Integer.class);
        return v == null ? 0 : v;
    }

    private BigDecimal decimalOf(String sql) {
        BigDecimal v = repo.jdbc().queryForObject(sql, new MapSqlParameterSource(), BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }
}
