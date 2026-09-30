package com.correoargentino.sga.service;


import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.NotFoundException;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.utils.ContractSql;
import com.correoargentino.sga.utils.ContratoValidator;

import org.apache.coyote.BadRequestException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.correoargentino.sga.utils.Campos.*;

@Service
public class ContractService {

    private static final String ENTIDAD_CONTRATO = "contrato";
    private static final String ENTIDAD_VALOR = "contrato_valor";
    private static final String CONTRATO_NO_ENCONTRADO = "Contrato no encontrado";

    private static final String ORIGEN_CONTRATO = "CONTRATO";
    private static final String ORIGEN_ACUERDO = "ACUERDO";
    private static final String ORIGEN_INDICE = "AJUSTE_INDICE";

    private static final int ESTADO_VIGENTE = 1;
    private static final int ESTADO_VENCIDO = 3;
    private static final int NUMERO_BASE = 1000;

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public ContractService(SgaRepository repo, AuditService audit, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    // =====================================================
    // Alta
    // =====================================================

    @Transactional
    public long create(Map<String, Object> body) throws BadRequestException {

        requireEdit();
        ContratoValidator.validar(body);

        Long indiceId = resolverIndice(body);
        Long inmuebleId = resolverInmueble(body);
        Long locadorId = resolverLocador(body);

        LocalDate inicio = asDateOrToday(body.getOrDefault("fechaInicio", LocalDate.now().toString()));
        int cantidadFacturas = ContratoValidator.cantidadFacturas(body);
        LocalDate vencimiento = ContratoValidator.vencimiento(body, inicio, cantidadFacturas);
        BigDecimal deposito = ContratoValidator.deposito(body);

        String numero = siguienteNumero();

        DatosContrato datos = new DatosContrato(
            inmuebleId,
            locadorId,
            acreedorId(body, locadorId),
            indiceId,
            asInt(body.getOrDefault("tipoContratoId", 1)),
            inicio,
            vencimiento,
            asDecimal(body.getOrDefault("importeTotal", 0)),
            deposito,
            asDecimal(body.getOrDefault("tolerancia", 3)),
            str(body.getOrDefault("periodicidad", "TRIMESTRAL")),
            asInt(body.getOrDefault("tipoComprobanteId", 1)),
            cantidadFacturas);

        long contratoId = insertarConId(
            ContractSql.INSERT_CONTRATO,
            datos.parametros(body).addValue("numero", numero));

        insertarFacturasPlanificadas(contratoId, facturasPlanificadas(body));
        insertarValor(contratoId, inicio, datos.importe(), ORIGEN_CONTRATO, null, null);

        auditar(contratoId, "CREAR", "Creó el contrato " + numero, numero);
        return contratoId;
    }

    // =====================================================
    // Edición
    // =====================================================

    @Transactional
    public void update(long id, Map<String, Object> body) throws BadRequestException {

        requireEdit();

        Map<String, Object> before = repo.getContractDetail(id);

        // ⚠ Orden original preservado: el índice nuevo se crea ANTES de verificar
        //   que el contrato exista. Para corregirlo, mover esta línea debajo del if.
        Long indiceId = resolverIndice(body);

        if (before == null) {
            throw new NotFoundException(CONTRATO_NO_ENCONTRADO);
        }

        ContratoValidator.validar(body);

        Long inmuebleId = resolverInmueble(body);
        Long locadorId = resolverLocador(body);
        List<Map<String, Object>> facturas = facturasPlanificadas(body);

        DatosContrato datos = new DatosContrato(
            inmuebleId,
            locadorId,
            acreedorId(body, locadorId),
            indiceId,
            asInt(body.get("tipoContratoId")),
            asDateOrToday(body.get("fechaInicio")),
            asDateOrToday(body.get("fechaVencimiento")),
            asDecimal(body.get("importeTotal")),
            asDecimal(body.get("deposito")),
            asDecimal(body.get("tolerancia")),
            str(body.get("periodicidad")),
            asInt(body.get("tipoComprobanteId")),
            facturas.size());

        repo.jdbc().update(
            ContractSql.UPDATE_CONTRATO,
            datos.parametros(body).addValue("id", id));

        reemplazarFacturasPlanificadas(id, facturas);
        actualizarImporteVigente(id, datos.importe(), nis(before));

        auditar(id, "EDITAR", "Actualizó datos del contrato", nis(before));
    }

    /** Cierra el valor vigente y abre uno nuevo (origen ACUERDO) solo si el importe cambió. */
    private void actualizarImporteVigente(long id, BigDecimal importe, String nis) {

        BigDecimal actual = importeVigente(id);

        if (actual != null && actual.compareTo(importe) == 0) {
            return;
        }

        MapSqlParameterSource params = new MapSqlParameterSource("id", id);

        repo.jdbc().update(ContractSql.CERRAR_VALOR_HOY, params);
        repo.jdbc().update(
            ContractSql.INSERT_VALOR_ACUERDO_HOY,
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("importe", importe));

        audit.log(ENTIDAD_CONTRATO, String.valueOf(id), "EDITAR", "Importe mensual", nis,
            actual == null ? null : actual.toPlainString(),
            importe.toPlainString());
    }

    private BigDecimal importeVigente(long id) {
        try {
            return repo.jdbc().queryForObject(
                ContractSql.IMPORTE_VIGENTE,
                new MapSqlParameterSource("id", id),
                BigDecimal.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // =====================================================
    // Baja
    // =====================================================

    @Transactional
    public void softDelete(long id) {

        requireEdit();
        Map<String, Object> before = contratoExistente(id);

        repo.jdbc().update(ContractSql.RESCINDIR, new MapSqlParameterSource("id", id));

        auditar(id, "ELIMINAR", "Eliminó (rescindió) el contrato", nis(before));
    }

    // =====================================================
    // Ajustes de importe
    // =====================================================

    @Transactional
    public void registrarAjuste(long id, Map<String, Object> body) throws BadRequestException {

        requireEdit();
        Map<String, Object> contrato = contratoExistente(id);

        String origen = str(body.get("origen"));
        if (!ORIGEN_INDICE.equals(origen) && !ORIGEN_ACUERDO.equals(origen)) {
            throw new BadRequestException("El origen debe ser ajuste por índice o acuerdo.");
        }
        boolean porIndice = ORIGEN_INDICE.equals(origen);

        BigDecimal importe = porIndice
            ? null
            : decimalPositivo(body.get("importe"), "El nuevo importe mensual debe ser mayor a cero.");

        LocalDate desde = fechaAjuste(body.get("desde"));

        Integer indiceId = null;
        BigDecimal coeficiente = null;

        if (porIndice) {
            indiceId = indiceExistente(body.get("indiceId"));
            coeficiente = decimalPositivo(body.get("coeficiente"), "El coeficiente debe ser mayor a cero.");
            importe = importeAjustadoPorIndice(id, coeficiente);
        }

        String importeAnterior = insertarValorAjuste(id, desde, importe, origen, indiceId, coeficiente);

        audit.log(ENTIDAD_VALOR, String.valueOf(id), "AJUSTE", "Registró ajuste de importe mensual",
            nis(contrato), importeAnterior, importe.toPlainString());
    }

    @Transactional
    public boolean aplicarAjusteIndiceSistema(long contratoId,
                                              LocalDate desde,
                                              BigDecimal importe,
                                              int indiceId,
                                              BigDecimal coeficiente,
                                              String nis,
                                              String importeAnterior) {

        boolean yaAplicado = existe(
            ContractSql.VALORES_EN_FECHA,
            new MapSqlParameterSource()
                .addValue("id", contratoId)
                .addValue("desde", desde));

        if (yaAplicado) {
            return false;
        }

        try {
            insertarValorAjuste(contratoId, desde, importe, ORIGEN_INDICE, indiceId, coeficiente);
        } catch (BadRequestException e) {
            return false;
        }

        audit.logAs("rpa", ENTIDAD_VALOR, String.valueOf(contratoId), "AJUSTE",
            "Registró ajuste automático por índice", nis, importeAnterior, importe.toPlainString());
        return true;
    }

    private static LocalDate fechaAjuste(Object raw) throws BadRequestException {
        if (isBlank(raw)) {
            throw new BadRequestException("La fecha de inicio del ajuste es obligatoria.");
        }
        try {
            return LocalDate.parse(str(raw).substring(0, 10));
        } catch (RuntimeException e) {
            throw new BadRequestException("La fecha de inicio del ajuste no es válida.");
        }
    }

    private Integer indiceExistente(Object raw) throws BadRequestException {
        Integer indiceId = asInt(raw);

        if (indiceId == null) {
            throw new BadRequestException("Seleccioná el índice del ajuste.");
        }
        if (!existe(ContractSql.INDICES_POR_ID, new MapSqlParameterSource("id", indiceId))) {
            throw new BadRequestException("El índice seleccionado no existe.");
        }
        return indiceId;
    }

    private BigDecimal importeAjustadoPorIndice(long contratoId, BigDecimal coeficiente)
            throws BadRequestException {

        Map<String, Object> vigente = valorVigente(contratoId);
        BigDecimal actual = vigente == null ? null : plainDecimal(vigente.get("importe"));

        if (actual == null || actual.signum() <= 0) {
            throw new BadRequestException(
                "El contrato no tiene un importe vigente para aplicar el índice.");
        }
        return actual.multiply(coeficiente).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Cierra el valor vigente el día anterior a {@code desde} e inserta el nuevo.
     * @return el importe del valor cerrado, o null si no había.
     */
    private String insertarValorAjuste(long contratoId,
                                       LocalDate desde,
                                       BigDecimal importe,
                                       String origen,
                                       Integer indiceId,
                                       BigDecimal coeficiente) throws BadRequestException {

        Map<String, Object> vigente = valorVigente(contratoId);
        String importeAnterior = null;

        if (vigente != null && vigente.get("desde") != null) {

            LocalDate desdeActual = AjusteIndiceCalculo.toLocalDate(vigente.get("desde"));

            if (desdeActual != null && !desde.isAfter(desdeActual)) {
                throw new BadRequestException(
                    "La fecha del ajuste debe ser posterior al valor vigente (" + desdeActual + ").");
            }
            if (vigente.get("importe") != null) {
                importeAnterior = vigente.get("importe").toString();
            }

            repo.jdbc().update(
                ContractSql.CERRAR_VALOR,
                new MapSqlParameterSource()
                    .addValue("hasta", desde.minusDays(1))
                    .addValue("valorId", vigente.get("id")));
        }

        insertarValor(contratoId, desde, importe, origen, indiceId, coeficiente);
        return importeAnterior;
    }

    private Map<String, Object> valorVigente(long contratoId) {
        return repo.queryOne(ContractSql.VALOR_VIGENTE, new MapSqlParameterSource("id", contratoId));
    }

    private void insertarValor(long contratoId,
                               LocalDate desde,
                               BigDecimal importe,
                               String origen,
                               Integer indiceId,
                               BigDecimal coeficiente) {
        repo.jdbc().update(
            ContractSql.INSERT_VALOR,
            new MapSqlParameterSource()
                .addValue("contratoId", contratoId)
                .addValue("desde", desde)
                .addValue("importe", importe)
                .addValue("origen", origen)
                .addValue("indiceId", indiceId)
                .addValue("coeficiente", coeficiente));
    }

    // =====================================================
    // Inmuebles e índices (endpoints propios)
    // =====================================================

    @Transactional
    public long createInmueble(Map<String, Object> body) throws BadRequestException {

        requireEdit();

        if (isBlank(body.get("nis"))) {
            throw new BadRequestException("El NIS es obligatorio.");
        }
        if (isBlank(body.get("denominacion"))) {
            throw new BadRequestException("La unidad de negocio es obligatoria.");
        }
        return insertarInmueble(body);
    }

    @Transactional
    public long createIndice(Map<String, Object> body) throws BadRequestException {

        requireEdit();

        String codigo = str(body.get("codigo"));

        if (isBlank(codigo)) {
            throw new BadRequestException("El código del índice es obligatorio.");
        }
        if (isBlank(body.get("nombre"))) {
            throw new BadRequestException("El nombre del índice es obligatorio.");
        }
        
        return repo.insertIndice(body);
    }

    // =====================================================
    // Entidades relacionadas (compartido por create y update)
    // =====================================================

    /** Crea el índice si modoIndice = "nuevo"; si no, usa indiceId. */
    private Long resolverIndice(Map<String, Object> body) throws BadRequestException {

        if (!"nuevo".equals(str(body.get("modoIndice")))) {
            return asLong(body.get("indiceId"));
        }

        Map<String, Object> indice = new HashMap<>();
        indice.put("codigo", str(body.get("indiceCodigo")));
        indice.put("nombre", str(body.get("indiceNombre")));
        indice.put("fuente", str(body.get("indiceFuente")));

        return createIndice(indice);
    }

    /** Usa inmuebleId si viene; si no, crea el inmueble con los datos del body. */
    private Long resolverInmueble(Map<String, Object> body) {
        Long inmuebleId = asLong(body.get("inmuebleId"));
        return inmuebleId != null ? inmuebleId : insertarInmueble(body);
    }

    /** Crea el locador si vienen razonSocial + cuit; si no, exige locadorId. */
    private Long resolverLocador(Map<String, Object> body) throws BadRequestException {

        if (body.containsKey("razonSocial") && body.containsKey("cuit")) {
            body.put("cuit", ContratoValidator.normalizarCuit(body.get("cuit")));
            return insertarLocador(body);
        }

        Long locadorId = asLong(body.get("locadorId"));
        if (locadorId == null) {
            throw new BadRequestException(
                "Debe indicar el Locador o informar la Razón Social y CUIT.");
        }
        return locadorId;
    }

    private long insertarInmueble(Map<String, Object> body) {

        long inmuebleId = insertarConId(
            ContractSql.INSERT_INMUEBLE,
            new MapSqlParameterSource()
                .addValue("nis", str(body.getOrDefault("nis", "NIS-" + System.currentTimeMillis())))
                .addValue("denom", str(body.getOrDefault("denominacion", "Sin denominación")))
                .addValue("direccion", str(body.get("direccion")))
                .addValue("localidadId", asInt(body.get("localidadId")))
                .addValue("regionId", asInt(body.get("regionId")))
                .addValue("sup", asDecimal(body.get("superficieCubierta"))));

        Integer destinoId = asInt(body.get("destinoId"));
        if (destinoId != null) {
            repo.jdbc().update(
                ContractSql.INSERT_INMUEBLE_DESTINO,
                new MapSqlParameterSource()
                    .addValue("i", inmuebleId)
                    .addValue("d", destinoId));
        }
        return inmuebleId;
    }

    /** El CUIT del body ya viene normalizado por resolverLocador. */
    private long insertarLocador(Map<String, Object> body) throws BadRequestException {

        String cuit = str(body.get("cuit"));

        if (existe(ContractSql.LOCADORES_POR_CUIT, new MapSqlParameterSource("cuit", cuit))) {
            throw new BadRequestException("Ya existe un locador registrado con el CUIT " + cuit + ".");
        }

        return insertarConId(
            ContractSql.INSERT_LOCADOR,
            new MapSqlParameterSource()
                .addValue("razonSocial", str(body.get("razonSocial")))
                .addValue("cuit", cuit)
                .addValue("email", str(body.get("email")))
                .addValue("telefono", str(body.get("telefono"))));
    }

    private Long acreedorId(Map<String, Object> body, Long locadorId) throws BadRequestException {
        if (body.containsKey("acreedorSapCodigo")) {
            return resolverAcreedor(blankToNull(body.get("acreedorSapCodigo")), locadorId);
        }
        return asLong(body.get("acreedorSapId"));
    }

    /** Busca el acreedor por código SAP; si no existe, lo crea asociado al locador. */
    private Long resolverAcreedor(String codigo, Long locadorId) throws BadRequestException {

        if (codigo == null) {
            return null;
        }

        List<Long> ids = repo.jdbc().query(
            ContractSql.ACREEDOR_POR_CODIGO,
            new MapSqlParameterSource("codigo", codigo),
            (rs, rowNum) -> rs.getLong("id"));

        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        if (locadorId == null) {
            throw new BadRequestException("No se puede crear el acreedor SAP sin un locador.");
        }

        return insertarConId(
            ContractSql.INSERT_ACREEDOR,
            new MapSqlParameterSource()
                .addValue("codigo", codigo)
                .addValue("locadorId", locadorId));
    }

    // =====================================================
    // Facturas planificadas
    // =====================================================

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> facturasPlanificadas(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("facturas_planificadas");
    }

    private void reemplazarFacturasPlanificadas(long contratoId, List<Map<String, Object>> facturas) {
        repo.jdbc().update(ContractSql.DELETE_PLANIFICADAS, new MapSqlParameterSource("id", contratoId));
        insertarFacturasPlanificadas(contratoId, facturas);
    }

    /** Una fila por elemento de la lista, nunca más. */
    private void insertarFacturasPlanificadas(long contratoId, List<Map<String, Object>> facturas) {
        for (Map<String, Object> factura : facturas) {
            repo.jdbc().update(
                ContractSql.INSERT_PLANIFICADA,
                new MapSqlParameterSource()
                    .addValue("contratoId", contratoId)
                    .addValue("porcentaje", asInt(factura.get("porcentaje")))
                    .addValue("monto", asDecimal(factura.get("importe"))));
        }
    }

    // =====================================================
    // Utilidades
    // =====================================================

    private void requireEdit() {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no tiene permiso para modificar contratos.");
        }
    }

    private Map<String, Object> contratoExistente(long id) {
        Map<String, Object> contrato = repo.getContractDetail(id);
        if (contrato == null) {
            throw new NotFoundException(CONTRATO_NO_ENCONTRADO);
        }
        return contrato;
    }

    private String siguienteNumero() {
        Integer max = repo.jdbc().queryForObject(
            ContractSql.ULTIMO_NUMERO, new MapSqlParameterSource(), Integer.class);

        return "C-%06d".formatted((max == null ? NUMERO_BASE : max) + 1);
    }

    private long insertarConId(String sql, MapSqlParameterSource params) {
        KeyHolder kh = new GeneratedKeyHolder();
        repo.jdbc().update(sql, params, kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    /** true si el SELECT COUNT(*) devuelve más de 0. */
    private boolean existe(String sqlCount, MapSqlParameterSource params) {
        Integer cantidad = repo.jdbc().queryForObject(sqlCount, params, Integer.class);
        return cantidad != null && cantidad > 0;
    }

    private static BigDecimal decimalPositivo(Object raw, String mensaje) throws BadRequestException {
        BigDecimal valor = plainDecimal(raw);
        if (valor == null || valor.signum() <= 0) {
            throw new BadRequestException(mensaje);
        }
        return valor;
    }

    private static int estadoSegunVencimiento(LocalDate vencimiento) {
        return vencimiento.isBefore(LocalDate.now()) ? ESTADO_VENCIDO : ESTADO_VIGENTE;
    }

    private static String nis(Map<String, Object> contrato) {
        return String.valueOf(contrato.get("nis"));
    }

    private void auditar(long id, String accion, String detalle, String referencia) {
        audit.log(ENTIDAD_CONTRATO, String.valueOf(id), accion, detalle, referencia, null, null);
    }

    // =====================================================
    // Datos del contrato (compartido por INSERT y UPDATE)
    // =====================================================

    private record DatosContrato(
        Long inmuebleId,
        Long locadorId,
        Long acreedorId,
        Long indiceId,
        Integer tipoContratoId,
        LocalDate inicio,
        LocalDate vencimiento,
        BigDecimal importe,
        BigDecimal deposito,
        BigDecimal tolerancia,
        String periodicidad,
        Integer tipoComprobanteId,
        int cantidadFacturas
    ) {

        MapSqlParameterSource parametros(Map<String, Object> body) {
            return new MapSqlParameterSource()
                .addValue("inmuebleId", inmuebleId)
                .addValue("locadorId", locadorId)
                .addValue("acreedorId", acreedorId)
                .addValue("tipoContrato", tipoContratoId)
                .addValue("estadoId", estadoSegunVencimiento(vencimiento))
                .addValue("inicio", inicio)
                .addValue("venc", vencimiento)
                .addValue("importe", importe)
                .addValue("deposito", deposito)
                .addValue("indiceId", indiceId)
                .addValue("periodicidad", periodicidad)
                .addValue("tipoComp", tipoComprobanteId)
                .addValue("tolerancia", tolerancia)
                .addValue("tipoFacturacion", str(body.getOrDefault("tipoFacturacion", "mensual")))
                .addValue("cantidadFacturas", cantidadFacturas)
                .addValue("cecoSap", blankToNull(body.get("cecoSap")))
                .addValue("divisionSap", blankToNull(body.get("divisionSap")))
                .addValue("cuentaGasto", blankToNull(body.get("cuentaGasto")))
                .addValue("indicadorImpuesto", blankToNull(body.get("indicadorImpuesto")));
        }
    }
}