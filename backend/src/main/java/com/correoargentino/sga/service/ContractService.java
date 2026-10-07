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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Locador ya resuelto en la base (existente o recién creado). */
    private record LocadorContrato(long locadorId, Long acreedorId, int porcentaje) {}

    @Transactional
    public long create(Map<String, Object> body) throws BadRequestException {

        requireEdit();
        ContratoValidator.validar(body);
        List<ContratoValidator.LocadorBody> locadoresBody = ContratoValidator.locadores(body);

        Long indiceId = resolverIndice(body);
        Long inmuebleId = resolverInmueble(body);
        List<LocadorContrato> locadores = resolverLocadores(locadoresBody);
        LocadorContrato principal = locadores.get(0);

        LocalDate inicio = asDateOrToday(body.getOrDefault("fechaInicio", LocalDate.now().toString()));
        int cantidadFacturas = locadores.size();          // una factura planificada por locador
        LocalDate vencimiento = ContratoValidator.vencimiento(body, inicio, cantidadFacturas);
        BigDecimal deposito = ContratoValidator.deposito(body);

        String numero = siguienteNumero();

        DatosContrato datos = new DatosContrato(
            inmuebleId,
            principal.locadorId(),          // ya no se guarda en contrato; el INSERT lo ignora
            principal.acreedorId(),         // contrato.acreedor_sap_id = acreedor del primer locador
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

        insertarLocadoresYPlanificadas(contratoId, locadores, datos.importe());
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

        Long indiceId = resolverIndice(body);

        if (before == null) {
            throw new NotFoundException(CONTRATO_NO_ENCONTRADO);
        }

        ContratoValidator.validar(body);
        List<ContratoValidator.LocadorBody> locadoresBody = ContratoValidator.locadores(body);

        Long inmuebleId = resolverInmueble(body);
        List<LocadorContrato> locadores = resolverLocadores(locadoresBody);
        LocadorContrato principal = locadores.get(0);

        DatosContrato datos = new DatosContrato(
            inmuebleId,
            principal.locadorId(),          
            principal.acreedorId(),         
            indiceId,
            asInt(body.get("tipoContratoId")),
            asDateOrToday(body.get("fechaInicio")),
            asDateOrToday(body.get("fechaVencimiento")),
            asDecimal(body.get("importeTotal")),
            asDecimal(body.get("deposito")),
            asDecimal(body.get("tolerancia")),
            str(body.get("periodicidad")),
            asInt(body.get("tipoComprobanteId")),
            locadores.size());              

        repo.jdbc().update(
            ContractSql.UPDATE_CONTRATO,
            datos.parametros(body).addValue("id", id));

        reemplazarLocadoresYPlanificadas(id, locadores, datos.importe());
        actualizarImporteVigente(id, datos.importe(), nis(before));

        Map<String, Object> after = repo.getContractDetail(id);

        auditar(id, "EDITAR", "Actualizó datos del contrato", nis(before));
        auditarCambios(id, before, after, nis(before));
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
    // Locadores
    // =====================================================

    private List<LocadorContrato> resolverLocadores(List<ContratoValidator.LocadorBody> locadores)
            throws BadRequestException {

        List<LocadorContrato> result = new ArrayList<>();
        Set<Long> usados = new HashSet<>();

        for (ContratoValidator.LocadorBody l : locadores) {

            long locadorId = l.locadorId() != null
                ? actualizarLocadorExistente(l)
                : crearOReutilizarLocador(l);

            // p. ej. un "nuevo" cuyo CUIT ya pertenece a otro locador elegido en la lista
            if (!usados.add(locadorId)) {
                throw new BadRequestException("Hay un locador repetido en el contrato.");
            }

            Long acreedorId = resolverAcreedor(l.acreedorSapCodigo(), locadorId);
            result.add(new LocadorContrato(locadorId, acreedorId, l.porcentaje()));
        }

        return result;
    }

    private long actualizarLocadorExistente(ContratoValidator.LocadorBody l) throws BadRequestException {

        Integer existe = repo.jdbc().queryForObject(
            ContractSql.LOCADOR_EXISTE,
            new MapSqlParameterSource("id", l.locadorId()),
            Integer.class);

        if (existe == null || existe == 0) {
            throw new BadRequestException("El locador " + l.locadorId() + " no existe.");
        }

        repo.jdbc().update(ContractSql.UPDATE_LOCADOR_SAP, sapParams(l).addValue("id", l.locadorId()));
        return l.locadorId();
    }

    /** Si el CUIT ya existe, usa ese locador y le actualiza los datos SAP; si no, lo crea. */
    private long crearOReutilizarLocador(ContratoValidator.LocadorBody l) {

        Long existente = queryLongOrNull(
            ContractSql.LOCADOR_ID_POR_CUIT,
            new MapSqlParameterSource("cuit", l.cuit()));

        if (existente != null) {
            repo.jdbc().update(ContractSql.UPDATE_LOCADOR_SAP, sapParams(l).addValue("id", existente));
            return existente;
        }

        return insertarConId(
            ContractSql.INSERT_LOCADOR,
            sapParams(l)
                .addValue("razonSocial", l.razonSocial())
                .addValue("cuit", l.cuit()));
    }

    private MapSqlParameterSource sapParams(ContratoValidator.LocadorBody l) {
        return new MapSqlParameterSource()
            .addValue("email", l.email())
            .addValue("telefono", l.telefono())
            .addValue("cbu", l.cbu())
            .addValue("cuentaGasto", l.cuentaGasto())
            .addValue("divisionSap", l.divisionSap())
            .addValue("cecoSap", l.cecoSap())
            .addValue("indicadorImpuesto", l.indicadorImpuesto());
    }

    /**
     * Con código: lo busca y, si no existe, lo crea para el locador.
     * Sin código: usa el primer acreedor que ya tenga el locador (o null).
     */
    private Long resolverAcreedor(String codigo, long locadorId) {

        if (codigo == null) {
            return queryLongOrNull(
                ContractSql.ACREEDOR_POR_LOCADOR,
                new MapSqlParameterSource("locadorId", locadorId));
        }

        Long id = queryLongOrNull(
            ContractSql.ACREEDOR_POR_CODIGO,
            new MapSqlParameterSource("codigo", codigo));

        if (id != null) {
            return id;
        }

        return insertarConId(
            ContractSql.INSERT_ACREEDOR,
            new MapSqlParameterSource()
                .addValue("codigo", codigo)
                .addValue("locadorId", locadorId));
    }

    // =====================================================
    // contrato_locador + factura_planificada
    // =====================================================

    /**
     * Por cada locador inserta primero contrato_locador (lo exige la FK compuesta)
     * y después su factura planificada. La última absorbe el residuo de redondeo.
     */
    private void insertarLocadoresYPlanificadas(long contratoId,
                                                List<LocadorContrato> locadores,
                                                BigDecimal importeTotal) {

        BigDecimal importe = importeTotal == null ? BigDecimal.ZERO : importeTotal;
        BigDecimal acumulado = BigDecimal.ZERO;

        for (int k = 0; k < locadores.size(); k++) {

            LocadorContrato l = locadores.get(k);
            boolean ultimo = k == locadores.size() - 1;

            MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("contratoId", contratoId)
                .addValue("locadorId", l.locadorId());

            repo.jdbc().update(ContractSql.INSERT_CONTRATO_LOCADOR, p);

            BigDecimal monto = ultimo
                ? importe.subtract(acumulado)
                : importe.multiply(BigDecimal.valueOf(l.porcentaje()))
                         .divide(CIEN, 2, RoundingMode.HALF_UP);

            acumulado = acumulado.add(monto);

            repo.jdbc().update(ContractSql.INSERT_PLANIFICADA, p
                .addValue("porcentaje", l.porcentaje())
                .addValue("monto", monto));
        }
    }

    private Long queryLongOrNull(String sql, MapSqlParameterSource p) {
        return repo.jdbc().query(sql, p, rs -> rs.next() ? rs.getLong(1) : null);
    }

    /**
     * Borra las planificadas y los locadores del contrato (en ese orden, por la FK
     * compuesta factura_planificada → contrato_locador) y los vuelve a insertar.
     */
    private void reemplazarLocadoresYPlanificadas(long contratoId,
                                                  List<LocadorContrato> locadores,
                                                  BigDecimal importeTotal) {

        MapSqlParameterSource p = new MapSqlParameterSource("id", contratoId);

        repo.jdbc().update(ContractSql.DELETE_PLANIFICADAS, p);
        repo.jdbc().update(ContractSql.DELETE_CONTRATO_LOCADORES, p);

        insertarLocadoresYPlanificadas(contratoId, locadores, importeTotal);
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
    
        /** Campos del contrato que se auditan: clave en getContractDetail → nombre que se muestra. */
    private static final Map<String, String> CAMPOS_AUDITADOS = new LinkedHashMap<>();
    static {
        CAMPOS_AUDITADOS.put("nis", "NIS");
        CAMPOS_AUDITADOS.put("denom", "Unidad de negocio");
        CAMPOS_AUDITADOS.put("direccion", "Dirección");
        CAMPOS_AUDITADOS.put("region", "Región");
        CAMPOS_AUDITADOS.put("localidad", "Localidad");
        CAMPOS_AUDITADOS.put("destino", "Destino / uso");
        CAMPOS_AUDITADOS.put("supCubierta", "Superficie cubierta");
        CAMPOS_AUDITADOS.put("tipo", "Tipo de contrato");
        CAMPOS_AUDITADOS.put("inicio", "Fecha de inicio");
        CAMPOS_AUDITADOS.put("vencimiento", "Fecha de vencimiento");
        CAMPOS_AUDITADOS.put("valorActual", "Importe mensual");
        CAMPOS_AUDITADOS.put("deposito", "Depósito de garantía");
        CAMPOS_AUDITADOS.put("tolerancia", "Tolerancia");
        CAMPOS_AUDITADOS.put("indiceCodigo", "Índice de ajuste");
        CAMPOS_AUDITADOS.put("periodicidad", "Frecuencia de ajuste");
        CAMPOS_AUDITADOS.put("tipoFacturacion", "Tipo de facturación");
        CAMPOS_AUDITADOS.put("acreedorSap", "Acreedor SAP");
    }

    /** Datos de cada locador que se auditan. */
    private static final Map<String, String> CAMPOS_LOCADOR = new LinkedHashMap<>();
    static {
        CAMPOS_LOCADOR.put("razonSocial", "Razón social");
        CAMPOS_LOCADOR.put("cuit", "CUIT");
        CAMPOS_LOCADOR.put("email", "Contacto");
        CAMPOS_LOCADOR.put("telefono", "Teléfono");
        CAMPOS_LOCADOR.put("cbu", "CBU");
        CAMPOS_LOCADOR.put("acreedorSap", "Acreedor SAP");
        CAMPOS_LOCADOR.put("cecoSap", "CeCo SAP");
        CAMPOS_LOCADOR.put("divisionSap", "División SAP");
        CAMPOS_LOCADOR.put("cuentaGasto", "Cuenta de gasto");
        CAMPOS_LOCADOR.put("indicadorImpuesto", "Indicador de impuestos");
        CAMPOS_LOCADOR.put("porcentaje", "Porcentaje de factura");
    }

    private void auditarCambios(long id, Map<String, Object> before, Map<String, Object> after, String ref) {

        // 1) datos del contrato
        for (Map.Entry<String, String> campo : CAMPOS_AUDITADOS.entrySet()) {
            registrarSiCambio(id, campo.getValue(),
                before.get(campo.getKey()), after.get(campo.getKey()), ref);
        }

        // 2) locadores: agregados, quitados y datos modificados
        Map<String, Map<String, Object>> locAntes = locadoresPorId(before);
        Map<String, Map<String, Object>> locDespues = locadoresPorId(after);

        for (Map.Entry<String, Map<String, Object>> e : locAntes.entrySet()) {
            if (!locDespues.containsKey(e.getKey())) {
                registrar(id, "Locador quitado", resumenLocador(e.getValue()), "—", ref);
            }
        }

        for (Map.Entry<String, Map<String, Object>> e : locDespues.entrySet()) {
            Map<String, Object> nuevo = e.getValue();
            Map<String, Object> viejo = locAntes.get(e.getKey());

            if (viejo == null) {
                registrar(id, "Locador agregado", "—", resumenLocador(nuevo), ref);
                continue;
            }

            String nombre = texto(nuevo.get("razonSocial"));
            for (Map.Entry<String, String> campo : CAMPOS_LOCADOR.entrySet()) {
                registrarSiCambio(id, campo.getValue() + " (" + nombre + ")",
                    viejo.get(campo.getKey()), nuevo.get(campo.getKey()), ref);
            }
        }
    }

    /**
     * Locadores del detalle indexados por id, con el porcentaje de su
     * factura planificada agregado como "porcentaje".
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> locadoresPorId(Map<String, Object> detalle) {

        Map<String, Object> porcentajes = new HashMap<>();
        Object plan = detalle.get("facturas_planificadas");
        if (plan instanceof List<?> lista) {
            for (Object o : lista) {
                Map<String, Object> fp = (Map<String, Object>) o;
                porcentajes.put(texto(fp.get("locadorId")), fp.get("porcentaje"));
            }
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Object locs = detalle.get("locadores");
        if (locs instanceof List<?> lista) {
            for (Object o : lista) {
                Map<String, Object> l = new HashMap<>((Map<String, Object>) o);
                String key = texto(l.get("id"));
                l.put("porcentaje", porcentajes.get(key));
                result.put(key, l);
            }
        }
        return result;
    }

    private String resumenLocador(Map<String, Object> l) {
        return texto(l.get("razonSocial")) + " · " + texto(l.get("cuit"))
            + " · " + texto(l.get("porcentaje")) + "%";
    }

    private void registrarSiCambio(long id, String campo, Object antes, Object despues, String ref) {
        String a = texto(antes);
        String d = texto(despues);
        if (!a.equals(d)) {
            registrar(id, campo, a, d, ref);
        }
    }

    private void registrar(long id, String campo, String antes, String despues, String ref) {
        audit.log(ENTIDAD_CONTRATO, String.valueOf(id), "EDITAR", campo, ref, antes, despues);
    }

    /**
     * Normaliza para comparar y mostrar: null → "—", números sin ceros de más
     * (3.00 → 3), fechas como yyyy-MM-dd y textos sin espacios de relleno.
     */
    private static String texto(Object v) {
        if (v == null) return "—";
        if (v instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
        if (v instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate().toString();
        String s = v.toString().trim();
        return s.isEmpty() ? "—" : s;
    }
}