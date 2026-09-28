package com.correoargentino.sga.service;

import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.utils.FacturaValidada;
import com.correoargentino.sga.utils.FacturaValidator;
import com.correoargentino.sga.utils.InvoiceSql;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private static final String ENTIDAD = "factura";
    private static final String SIN_REFERENCIA = "—";
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.03");

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final NotificationService notificationService;
    private final FacturaValidator validator;

    public InvoiceService(SgaRepository repo,
                          AuditService audit,
                          CurrentUserProvider currentUser,
                          NotificationService notificationService,
                          FacturaValidator validator) {
        this.repo = repo;
        this.audit = audit;
        this.currentUser = currentUser;
        this.notificationService = notificationService;
        this.validator = validator;
    }

    // =====================================================
    // Consultas
    // =====================================================

    public List<Map<String, Object>> planificadas() {
        return repo.query(InvoiceSql.PLANIFICADAS, new MapSqlParameterSource());
    }

    public List<Map<String, Object>> unassigned() {
        List<Map<String, Object>> rows =
            repo.query(InvoiceSql.SIN_ASIGNAR, new MapSqlParameterSource());

        for (Map<String, Object> factura : rows) {
            factura.put("sugerencias", sugerenciasPara(factura.get("cuit")));
        }
        return rows;
    }

    private List<Map<String, Object>> sugerenciasPara(Object cuit) {
        List<Map<String, Object>> sugerencias = repo.query(
            InvoiceSql.SUGERENCIAS_POR_CUIT,
            new MapSqlParameterSource("cuit", cuit));

        return sugerencias.isEmpty()
            ? repo.query(InvoiceSql.SUGERENCIAS_TODAS, new MapSqlParameterSource())
            : sugerencias;
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> factura = repo.queryOne(
            InvoiceSql.DETALLE, new MapSqlParameterSource("id", id));

        if (factura == null) {
            throw new NotFoundException("Factura no encontrada");
        }
        return factura;
    }

    // =====================================================
    // Alta
    // =====================================================

    @Transactional
    public long create(Map<String, Object> body) throws BadRequestException {

        requireEdit();

        FacturaValidada factura = validator.validar(body);
        long id = insertar(factura);

        boolean asignada = autoAsignar(id, factura);

        auditar(id, "CREAR", "Creó una factura manual", SIN_REFERENCIA);

        if (!asignada) {
            notificar("La factura de comprobante '" + factura.comprobante()
                + "' no pudo ser asignada automáticamente.");
        }
        return id;
    }

    private long insertar(FacturaValidada factura) {
        KeyHolder kh = new GeneratedKeyHolder();

        repo.jdbc().update(
            InvoiceSql.INSERT,
            camposComunes(factura)
                .addValue("tipoComp", factura.tipoComprobanteId())
                .addValue("puntoVenta", factura.puntoVenta())
                .addValue("cae", factura.cae())
                .addValue("fechaVtoCae", factura.fechaVtoCae())
                .addValue("moneda", factura.moneda()),
            kh,
            new String[]{"id"});

        return kh.getKey().longValue();
    }

    // =====================================================
    // Edición
    // =====================================================

    @Transactional
    public void update(long id, Map<String, Object> body) throws BadRequestException {

        requireEdit();
        get(id);

        FacturaValidada factura = validator.validar(body);

        repo.jdbc().update(
            InvoiceSql.UPDATE,
            camposComunes(factura).addValue("id", id));

        autoAsignar(id, factura);

        auditar(id, "EDITAR", "Editó la factura", SIN_REFERENCIA);
    }

    /** Parámetros compartidos por el INSERT y el UPDATE. */
    private static MapSqlParameterSource camposComunes(FacturaValidada f) {
        return new MapSqlParameterSource()
            .addValue("cuit", f.cuit())
            .addValue("razon", f.razonSocial())
            .addValue("comprobante", f.comprobante())
            .addValue("total", f.total())
            .addValue("neto", f.neto())
            .addValue("iva", f.iva())
            .addValue("periodo", f.periodo())
            .addValue("fechaEmision", f.fechaEmision())
            .addValue("obs", f.observaciones());
    }

    // =====================================================
    // Auto-asignación (compartida por create y update)
    // =====================================================

    /** Asigna la factura si hay exactamente un locador y un contrato vigente. */
    private boolean autoAsignar(long facturaId, FacturaValidada factura)
            throws BadRequestException {

        Optional<Long> contratoId = unicoId(
                InvoiceSql.LOCADOR_POR_CUIT,
                new MapSqlParameterSource("cuit", factura.cuit()))
            .flatMap(locadorId -> unicoId(
                InvoiceSql.CONTRATO_VIGENTE_DEL_LOCADOR,
                new MapSqlParameterSource()
                    .addValue("locadorId", locadorId)
                    .addValue("periodo", factura.periodo())));

        if (contratoId.isEmpty()) {
            return false;
        }

        assign(facturaId, contratoId.get());
        return true;
    }

    /** Devuelve el id solo si la consulta trae exactamente una fila. */
    private Optional<Long> unicoId(String sql, MapSqlParameterSource params) {
        List<Long> ids = repo.jdbc().query(
            sql, params, (rs, rowNum) -> rs.getLong("id"));

        return ids.size() == 1 ? Optional.of(ids.get(0)) : Optional.empty();
    }

    // =====================================================
    // Asignación a contrato
    // =====================================================

    @Transactional
    public void assign(long id, long contratoId) throws BadRequestException {

        requireEdit();

        Map<String, Object> contrato = requerir(
            InvoiceSql.CONTRATO_CON_INMUEBLE,
            new MapSqlParameterSource("contratoId", contratoId),
            "Contrato no encontrado");

        Map<String, Object> factura = requerir(
            InvoiceSql.FACTURA_PARA_ASIGNAR,
            new MapSqlParameterSource("facturaId", id),
            "Factura no encontrada");

        if (factura.get("periodoFacturado") == null) {
            throw new BadRequestException("La factura no tiene un período facturado.");
        }

        Map<String, Object> conciliacion = requerir(
            InvoiceSql.CONCILIACION_DEL_CONTRATO,
            new MapSqlParameterSource("contratoId", contratoId),
            "No existe una conciliación para el contrato " + contratoId + ".");

        long conciliacionId = numero(conciliacion.get("id")).longValue();
        BigDecimal esperado = decimal(conciliacion.get("importeEsperado"));

        MapSqlParameterSource relacion = new MapSqlParameterSource()
            .addValue("facturaId", id)
            .addValue("contratoId", contratoId)
            .addValue("conciliacionId", conciliacionId);

        repo.jdbc().update(InvoiceSql.BORRAR_RELACIONES, relacion);

        int updated = repo.jdbc().update(
            InvoiceSql.ASIGNAR_CONTRATO,
            new MapSqlParameterSource()
                .addValue("facturaId", id)
                .addValue("contratoId", contratoId)
                .addValue("inmuebleId", contrato.get("inmuebleId")));

        if (updated == 0) {
            throw new NotFoundException("No se pudo asignar la factura.");
        }

        repo.jdbc().update(InvoiceSql.CREAR_RELACION, relacion);

        recalcularConciliacion(conciliacionId, esperado, relacion);

        String nis = (String) contrato.get("nis");
        String sucursal = (String) contrato.get("denom");

        notificar("La factura '" + factura.get("numeroComprobante")
            + "' se asignó al contrato con NIS '" + nis
            + "' y sucursal '" + sucursal + "'.");

        auditar(id, "ASIGNAR", "Asignó factura al contrato", nis + " · " + sucursal);
    }

    private void recalcularConciliacion(long conciliacionId,
                                        BigDecimal esperado,
                                        MapSqlParameterSource relacion) {

        Map<String, Object> resumen =
            repo.jdbc().queryForMap(InvoiceSql.RESUMEN_CONCILIACION, relacion);

        int cantidad = numero(resumen.get("cantidad")).intValue();
        BigDecimal facturado = decimal(resumen.get("totalFacturado"));

        repo.jdbc().update(
            InvoiceSql.ACTUALIZAR_CONCILIACION,
            new MapSqlParameterSource()
                .addValue("conciliacionId", conciliacionId)
                .addValue("importeFacturado", facturado)
                .addValue("estado", estadoDe(cantidad, facturado, esperado)));
    }

    /** SIN_FACTURA · OK · OK_CON_DIF · CON_DIFERENCIA (tolerancia del 3%). */
    private static String estadoDe(int cantidad, BigDecimal facturado, BigDecimal esperado) {
        if (cantidad == 0) {
            return "SIN_FACTURA";
        }
        if (facturado.compareTo(esperado) == 0) {
            return "OK";
        }
        BigDecimal tolerancia = esperado.abs().multiply(TOLERANCIA);
        BigDecimal desvio = facturado.subtract(esperado).abs();

        return desvio.compareTo(tolerancia) <= 0 ? "OK_CON_DIF" : "CON_DIFERENCIA";
    }

    // =====================================================
    // Utilidades
    // =====================================================

    private void requireEdit() {
        log.info(currentUser.currentRole().name());
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no puede modificar facturas.");
        }
    }

    private Map<String, Object> requerir(String sql,
                                         MapSqlParameterSource params,
                                         String mensaje) {
        Map<String, Object> fila = repo.queryOne(sql, params);
        if (fila == null) {
            throw new NotFoundException(mensaje);
        }
        return fila;
    }

    private void auditar(long id, String accion, String detalle, String referencia) {
        audit.log(ENTIDAD, String.valueOf(id), accion, detalle, referencia, null, null);
    }

    private void notificar(String texto) throws BadRequestException {
        notificationService.create(Map.of("texto", texto));
    }

    private static Number numero(Object valor) {
        return (Number) valor;
    }

    private static BigDecimal decimal(Object valor) {
        return valor == null ? BigDecimal.ZERO : (BigDecimal) valor;
    }
}