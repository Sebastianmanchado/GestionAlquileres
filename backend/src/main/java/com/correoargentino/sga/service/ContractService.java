package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;

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
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ContractService {

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    public ContractService(SgaRepository repo, AuditService audit, CurrentUserProvider currentUser) {
        this.repo = repo;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    private void requireEdit() {
        if (!currentUser.currentRole().canEdit()) {
            throw new ForbiddenException("El rol actual no tiene permiso para modificar contratos.");
        }
    }

    @Transactional
    public long create(Map<String, Object> body) throws BadRequestException {
        requireEdit();

        validateCreateBody(body);

        Long indiceId;

        if ("nuevo".equals(str(body.get("modoIndice")))) {
            Map<String, Object> indice = new HashMap<>();

            indice.put("codigo", str(body.get("indiceCodigo")));
            indice.put("nombre", str(body.get("indiceNombre")));
            indice.put("fuente", str(body.get("indiceFuente")));

            indiceId = createIndice(indice);
        } else {
            indiceId = asLong(body.get("indiceId"));
        }

        Long inmuebleId = asLong(body.get("inmuebleId"));

        if (inmuebleId == null) {
            inmuebleId = insertInmueble(body);
        }

        log.info("pasa inmueble");

        Long locadorId;

        if (body.containsKey("razonSocial") && body.containsKey("cuit")) {

            requireValidString(body, "razonSocial", "Razón Social");

            String cuit = normalizeAndValidateCuit(body.get("cuit"));

            body.put("cuit", cuit);

            locadorId = insertLocador(body);

        } else {

            locadorId = asLong(body.get("locadorId"));

            if (locadorId == null) {
                throw new BadRequestException(
                    "Debe indicar el Locador o informar la Razon Social y CUIT."
                );
            }
        }


        log.info("paso inmueble y locador");

        Integer tipoContrato = asInt(body.getOrDefault("tipoContratoId", 1));

        LocalDate inicio = asDate(
            body.getOrDefault(
                "fechaInicio",
                LocalDate.now().toString()
            )
        );


        int cantidadFacturas = asInt(body.getOrDefault("cantidad_facturas", 1));

        if (cantidadFacturas < 1) {
            throw new BadRequestException(
                "La cantidad de facturas debe ser mayor o igual a 1."
            );
        }

        LocalDate venc;
        Object vencBody = body.get("fechaVencimiento");

        if (vencBody == null || str(vencBody).isBlank()) {
            venc = inicio.plusMonths(cantidadFacturas);
        } else {
            venc = asDate(vencBody);
        }

        if (!venc.isAfter(inicio)) {
            throw new BadRequestException(
                "La fecha de vencimiento debe ser posterior a la fecha de inicio."
            );
        }

        BigDecimal importe = asDecimal(
            body.getOrDefault("importeTotal", 0)
        );

        log.info("pasa importe");

        BigDecimal deposito = asDecimal(body.get("deposito"));

        if (deposito != null){
            if (deposito.compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException(
                    "El deposito debe ser mayor o igual a 0."
                );
            }
        }

        BigDecimal tolerancia = asDecimal(
            body.getOrDefault("tolerancia", 3)
        );

        log.info("paso tolerancia");

        int estadoId = estadoFromVencimiento(venc);

        List<Map<String, Object>> facturas =
            (List<Map<String, Object>>) body.get("facturas_planificadas");

        String numero = nextContractNumber();

        int totalPorcentaje = facturas.stream()
            .mapToInt(f -> asInt(f.get("porcentaje")))
            .sum();

        if (totalPorcentaje != 100) {
            throw new BadRequestException(
                "La suma de los porcentajes de las facturas debe ser 100%."
            );
        }

        log.info("paso todo");

        KeyHolder kh = new GeneratedKeyHolder();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("numero", numero)
                .addValue("inmuebleId", inmuebleId)
                .addValue("locadorId", locadorId)
                .addValue("tipoContrato", tipoContrato)
                .addValue("estadoId", estadoId)
                .addValue("inicio", inicio)
                .addValue("venc", venc)
                .addValue("importe", importe)
                .addValue("deposito", deposito)
                .addValue("indiceId", indiceId)
                .addValue("periodicidad", str(body.getOrDefault("periodicidad", "TRIMESTRAL")))
                .addValue("tipoComp", asInt(body.getOrDefault("tipoComprobanteId", 1)))
                .addValue("tolerancia", tolerancia)
                .addValue("tipoFacturacion", str(body.getOrDefault("tipoFacturacion", "mensual")))
                .addValue("cantidad_facturas", asInt(body.getOrDefault("cantidad_facturas", 1)))
                .addValue("acreedorId", acreedorId(body, locadorId))
                .addValue("cecoSap", blankToNull(body.get("cecoSap")))
                .addValue("divisionSap", blankToNull(body.get("divisionSap")))
                .addValue("cuentaGasto", blankToNull(body.get("cuentaGasto")))
                .addValue("indicadorImpuesto", blankToNull(body.get("indicadorImpuesto")));
        repo.jdbc().update("""
            INSERT INTO contrato (numero, inmueble_id, locador_id, acreedor_sap_id, tipo_contrato_id, estado_contrato_id,
                                  fecha_inicio, fecha_vencimiento, moneda, importe_inicial, deposito_garantia,
                                  indice_ajuste_id, periodicidad_ajuste, tipo_comprobante_id, tolerancia_importe_pct, tipo_facturacion, cantidad_facturas,
                                  ceco_sap, division_sap, cuenta_gasto, indicador_impuesto)
            VALUES (:numero, :inmuebleId, :locadorId, :acreedorId, :tipoContrato, :estadoId,
                    :inicio, :venc, 'ARS', :importe, :deposito, :indiceId, :periodicidad, :tipoComp, :tolerancia, :tipoFacturacion, :cantidad_facturas,
                    :cecoSap, :divisionSap, :cuentaGasto, :indicadorImpuesto)
            """, p, kh, new String[]{"id"});
        long contratoId = kh.getKey().longValue();
        log.info("se inserto contrato");
        // Generacion de facturas planificadas
        
        for (Map<String, Object> factura : facturas) {
                Integer porcentaje = ((Number) factura.get("porcentaje")).intValue();
                BigDecimal monto = BigDecimal.valueOf(
                    ((Number) factura.get("importe")).doubleValue()
                );

                repo.jdbc().update("""
                    INSERT INTO factura_planificada
                        (contrato_id, porcentaje_esperado, monto_esperado, estado)
                    VALUES
                        (:c, :porcentaje, :monto, 'PENDIENTE')
                    """,
                    new MapSqlParameterSource()
                        .addValue("c", contratoId)
                        .addValue("porcentaje", porcentaje)
                        .addValue("monto", monto)
                );
            }
        

        log.info("se inserto facturas futuras");

        // valor vigente inicial
        repo.jdbc().update("""
            INSERT INTO contrato_valor (contrato_id, vigencia_desde, vigencia_hasta, importe_mensual, origen)
            VALUES (:c, :desde, NULL, :importe, 'CONTRATO')
            """, new MapSqlParameterSource().addValue("c", contratoId).addValue("desde", inicio).addValue("importe", importe));

        log.info("se inserto contrato valor");
        audit.log("contrato", String.valueOf(contratoId), "CREAR", "Creó el contrato " + numero,
                numero, null, null);
        return contratoId;

        
    }

    @Transactional
    public void update(long id, Map<String, Object> body)
            throws BadRequestException {

        requireEdit();

        // =========================================================
        // 1. VERIFICAR QUE EXISTA EL CONTRATO
        // =========================================================
        Map<String, Object> before = repo.getContractDetail(id);

        Long indiceId;

        if ("nuevo".equals(str(body.get("modoIndice")))) {
            Map<String, Object> indice = new HashMap<>();

            indice.put("codigo", str(body.get("indiceCodigo")));
            indice.put("nombre", str(body.get("indiceNombre")));
            indice.put("fuente", str(body.get("indiceFuente")));

            indiceId = createIndice(indice);
        } else {
            indiceId = asLong(body.get("indiceId"));
        }

        if (before == null) {
            throw new NotFoundException("Contrato no encontrado");
        }

        // =========================================================
        // 2. VALIDAR TODO ANTES DE TOCAR LA BASE
        // =========================================================
        validateCreateBody(body);

        // =========================================================
        // 3. OBTENER VALORES YA VALIDADOS
        // =========================================================
        Long inmuebleId = asLong(body.get("inmuebleId"));
        if (inmuebleId == null){
            inmuebleId = insertInmueble(body);
        } 

        Long locadorId;

        if (body.containsKey("razonSocial") && body.containsKey("cuit")) {

            requireValidString(body, "razonSocial", "Razón Social");

            String cuit = normalizeAndValidateCuit(body.get("cuit"));

            body.put("cuit", cuit);

            locadorId = insertLocador(body);

        } else {

            locadorId = asLong(body.get("locadorId"));

            if (locadorId == null) {
                throw new BadRequestException(
                    "Debe indicar el Locador o informar la Razón Social y CUIT."
                );
            }
        }
        
        Integer tipoContrato = asInt(body.get("tipoContratoId"));

        LocalDate inicio = asDate(body.get("fechaInicio"));
        LocalDate venc = asDate(body.get("fechaVencimiento"));

        BigDecimal importe = asDecimal(body.get("importeTotal"));
        BigDecimal deposito = asDecimal(body.get("deposito"));
        BigDecimal tolerancia = asDecimal(body.get("tolerancia"));

        Long acreedorSapId = acreedorId(body, locadorId);
        Integer tipoComprobanteId = asInt(body.get("tipoComprobanteId"));

        String periodicidad = str(body.get("periodicidad"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> facturas =
            (List<Map<String, Object>>) body.get("facturas_planificadas");

        int estadoId = estadoFromVencimiento(venc);

        // =========================================================
        // 4. ACTUALIZAR CONTRATO
        // =========================================================
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("inmuebleId", inmuebleId)
            .addValue("locadorId", locadorId)
            .addValue("acreedorId", acreedorSapId)
            .addValue("tipoContrato", tipoContrato)
            .addValue("inicio", inicio)
            .addValue("venc", venc)
            .addValue("estadoId", estadoId)
            .addValue("importe", importe)
            .addValue("deposito", deposito)
            .addValue("indiceId", indiceId)
            .addValue("periodicidad", periodicidad)
            .addValue("tipoComp", tipoComprobanteId)
            .addValue("tolerancia", tolerancia)
            .addValue("tipoFacturacion", str(body.getOrDefault("tipoFacturacion", "mensual")))
            .addValue("cantidadFacturas", facturas.size())
            .addValue("cecoSap", blankToNull(body.get("cecoSap")))
            .addValue("divisionSap", blankToNull(body.get("divisionSap")))
            .addValue("cuentaGasto", blankToNull(body.get("cuentaGasto")))
            .addValue("indicadorImpuesto", blankToNull(body.get("indicadorImpuesto")));

        repo.jdbc().update("""
            UPDATE contrato
            SET
                inmueble_id = :inmuebleId,
                locador_id = :locadorId,
                acreedor_sap_id = :acreedorId,
                tipo_contrato_id = :tipoContrato,
                estado_contrato_id = :estadoId,
                fecha_inicio = :inicio,
                fecha_vencimiento = :venc,
                importe_inicial = :importe,
                deposito_garantia = :deposito,
                indice_ajuste_id = :indiceId,
                periodicidad_ajuste = :periodicidad,
                tipo_comprobante_id = :tipoComp,
                tolerancia_importe_pct = :tolerancia,
                tipo_facturacion = :tipoFacturacion,
                cantidad_facturas = :cantidadFacturas,
                ceco_sap = :cecoSap,
                division_sap = :divisionSap,
                cuenta_gasto = :cuentaGasto,
                indicador_impuesto = :indicadorImpuesto
            WHERE id = :id
            """,
            p
        );

        // =========================================================
        // 5. ACTUALIZAR FACTURAS PLANIFICADAS
        // =========================================================

        repo.jdbc().update("""
            DELETE FROM factura_planificada
            WHERE contrato_id = :id
            """,
            new MapSqlParameterSource("id", id)
        );

        for (Map<String, Object> factura : facturas) {

            Integer porcentaje =
                asInt(factura.get("porcentaje"));

            BigDecimal monto =
                asDecimal(factura.get("importe"));

            repo.jdbc().update("""
                INSERT INTO factura_planificada
                    (
                        contrato_id,
                        porcentaje_esperado,
                        monto_esperado,
                        estado
                    )
                VALUES
                    (
                        :contratoId,
                        :porcentaje,
                        :monto,
                        'PENDIENTE'
                    )
                """,
                new MapSqlParameterSource()
                    .addValue("contratoId", id)
                    .addValue("porcentaje", porcentaje)
                    .addValue("monto", monto)
            );
        }

        // =========================================================
        // 6. ACTUALIZAR VALOR DEL CONTRATO
        // =========================================================

        BigDecimal actual = null;

        try {
            actual = repo.jdbc().queryForObject(
                """
                SELECT importe_mensual
                FROM contrato_valor
                WHERE contrato_id = :id
                AND vigencia_hasta IS NULL
                """,
                new MapSqlParameterSource("id", id),
                BigDecimal.class
            );
        } catch (EmptyResultDataAccessException e) {
            // No existe valor vigente.
        }

        if (actual == null || actual.compareTo(importe) != 0) {

            repo.jdbc().update(
                """
                UPDATE contrato_valor
                SET vigencia_hasta = CAST(GETDATE() AS DATE)
                WHERE contrato_id = :id
                AND vigencia_hasta IS NULL
                """,
                new MapSqlParameterSource("id", id)
            );

            repo.jdbc().update(
                """
                INSERT INTO contrato_valor
                    (
                        contrato_id,
                        vigencia_desde,
                        vigencia_hasta,
                        importe_mensual,
                        origen
                    )
                VALUES
                    (
                        :id,
                        CAST(GETDATE() AS DATE),
                        NULL,
                        :importe,
                        'ACUERDO'
                    )
                """,
                new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("importe", importe)
            );

            audit.log(
                "contrato",
                String.valueOf(id),
                "EDITAR",
                "Importe mensual",
                String.valueOf(before.get("nis")),
                actual == null ? null : actual.toPlainString(),
                importe.toPlainString()
            );
        }

        // =========================================================
        // 7. AUDITORIA
        // =========================================================

        audit.log(
            "contrato",
            String.valueOf(id),
            "EDITAR",
            "Actualizó datos del contrato",
            String.valueOf(before.get("nis")),
            null,
            null
        );
    }

    @Transactional
    public void softDelete(long id) {
        requireEdit();
        Map<String, Object> before = repo.getContractDetail(id);
        if (before == null) throw new NotFoundException("Contrato no encontrado");
        repo.jdbc().update("UPDATE contrato SET estado_contrato_id=4 WHERE id=:id", new MapSqlParameterSource("id", id));
        audit.log("contrato", String.valueOf(id), "ELIMINAR", "Eliminó (rescindió) el contrato",
                String.valueOf(before.get("nis")), null, null);
    }

    /* ---------- helpers ---------- */

    private long insertInmueble(Map<String, Object> body) {
        KeyHolder kh = new GeneratedKeyHolder();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("nis", str(body.getOrDefault("nis", "NIS-" + System.currentTimeMillis())))
                .addValue("denom", str(body.getOrDefault("denominacion", "Sin denominación")))
                .addValue("direccion", str(body.get("direccion")))
                .addValue("localidadId", asInt(body.get("localidadId")))
                .addValue("regionId", asInt(body.get("regionId")))
                .addValue("sup", asDecimal(body.get("superficieCubierta")));
        repo.jdbc().update("""
            INSERT INTO inmueble (nis, denominacion, direccion, localidad_id, region_id, superficie_cubierta_m2, activo)
            VALUES (:nis, :denom, :direccion, :localidadId, :regionId, :sup, 1)
            """, p, kh, new String[]{"id"});
        long inmuebleId = kh.getKey().longValue();
        Integer destinoId = asInt(body.get("destinoId"));
        if (destinoId != null) {
            repo.jdbc().update("INSERT INTO inmueble_destino (inmueble_id, destino_id) VALUES (:i, :d)",
                    new MapSqlParameterSource().addValue("i", inmuebleId).addValue("d", destinoId));
        }
        return inmuebleId;
    }

    private long insertLocador(Map<String, Object> body)
            throws BadRequestException {

        String cuit = normalizeAndValidateCuit(body.get("cuit"));
        String razonSocial = str(body.get("razonSocial"));

        requireValidString(body, "razonSocial", "Razón Social");

        // Verificar que no exista otro locador con el mismo CUIT
        Integer existe = repo.jdbc().queryForObject(
            """
            SELECT COUNT(*)
            FROM locador
            WHERE cuit = :cuit
            """,
            new MapSqlParameterSource()
                .addValue("cuit", cuit),
            Integer.class
        );

        if (existe != null && existe > 0) {
            throw new BadRequestException(
                "Ya existe un locador registrado con el CUIT " + cuit + "."
            );
        }

        KeyHolder kh = new GeneratedKeyHolder();

        repo.jdbc().update(
            """
            INSERT INTO locador (
                tipo_persona,
                razon_social,
                cuit,
                email,
                telefono,
                activo
            )
            VALUES (
                'JURIDICA',
                :razonSocial,
                :cuit,
                :email,
                :telefono,
                1
            )
            """,
            new MapSqlParameterSource()
                .addValue("razonSocial", razonSocial)
                .addValue("cuit", cuit)
                .addValue("email", str(body.get("email")))
                .addValue("telefono", str(body.get("telefono"))),
            kh,
            new String[]{"id"}
        );

        return kh.getKey().longValue();
    }

    private String nextContractNumber() {
        Integer max = repo.jdbc().queryForObject(
                "SELECT ISNULL(MAX(TRY_CAST(REPLACE(numero,'C-','') AS INT)),1000) FROM contrato",
                new MapSqlParameterSource(), Integer.class);
        int next = (max == null ? 1000 : max) + 1;
        return "C-" + String.format("%06d", next);
    }

    @Transactional
    public void registrarAjuste(long id, Map<String, Object> body) throws BadRequestException {
        requireEdit();
        Map<String, Object> contrato = repo.getContractDetail(id);
        if (contrato == null) throw new NotFoundException("Contrato no encontrado");

        String origen = str(body.get("origen"));
        if (!"AJUSTE_INDICE".equals(origen) && !"ACUERDO".equals(origen)) {
            throw new BadRequestException("El origen debe ser ajuste por índice o acuerdo.");
        }

        BigDecimal importe = null;
        if ("ACUERDO".equals(origen)) {
            importe = plainDecimal(body.get("importe"));
            if (importe == null || importe.signum() <= 0) {
                throw new BadRequestException("El nuevo importe mensual debe ser mayor a cero.");
            }
        }

        if (body.get("desde") == null || str(body.get("desde")).isBlank()) {
            throw new BadRequestException("La fecha de inicio del ajuste es obligatoria.");
        }
        LocalDate desde;
        try {
            desde = LocalDate.parse(str(body.get("desde")).substring(0, 10));
        } catch (RuntimeException e) {
            throw new BadRequestException("La fecha de inicio del ajuste no es válida.");
        }

        Integer indiceId = null;
        BigDecimal coeficiente = null;
        if ("AJUSTE_INDICE".equals(origen)) {
            indiceId = asInt(body.get("indiceId"));
            if (indiceId == null) throw new BadRequestException("Seleccioná el índice del ajuste.");
            Integer existe = repo.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM indice_ajuste WHERE id = :id",
                    new MapSqlParameterSource("id", indiceId),
                    Integer.class);
            if (existe == null || existe == 0) throw new BadRequestException("El índice seleccionado no existe.");
            coeficiente = plainDecimal(body.get("coeficiente"));
            if (coeficiente == null || coeficiente.signum() <= 0) {
                throw new BadRequestException("El coeficiente debe ser mayor a cero.");
            }
        }

        Map<String, Object> vigente = repo.queryOne("""
            SELECT TOP 1 id, vigencia_desde AS desde, importe_mensual AS importe
              FROM contrato_valor
             WHERE contrato_id = :id AND vigencia_hasta IS NULL
             ORDER BY vigencia_desde DESC
            """, new MapSqlParameterSource("id", id));

        if ("AJUSTE_INDICE".equals(origen)) {
            BigDecimal actual = vigente == null ? null : plainDecimal(vigente.get("importe"));
            if (actual == null || actual.signum() <= 0) {
                throw new BadRequestException("El contrato no tiene un importe vigente para aplicar el índice.");
            }
            importe = actual.multiply(coeficiente).setScale(2, RoundingMode.HALF_UP);
        }

        String importeAnterior = insertarValorAjuste(id, desde, importe, origen, indiceId, coeficiente);

        audit.log(
                "contrato_valor",
                String.valueOf(id),
                "AJUSTE",
                "Registró ajuste de importe mensual",
                String.valueOf(contrato.get("nis")),
                importeAnterior,
                importe.toPlainString()
        );
    }

    @Transactional
    public boolean aplicarAjusteIndiceSistema(
            long contratoId,
            LocalDate desde,
            BigDecimal importe,
            int indiceId,
            BigDecimal coeficiente,
            String nis,
            String importeAnterior) {
        Integer ya = repo.jdbc().queryForObject("""
                SELECT COUNT(*)
                  FROM contrato_valor
                 WHERE contrato_id = :id AND vigencia_desde = :desde
                """, new MapSqlParameterSource()
                .addValue("id", contratoId)
                .addValue("desde", desde), Integer.class);
        if (ya != null && ya > 0) return false;

        try {
            insertarValorAjuste(contratoId, desde, importe, "AJUSTE_INDICE", indiceId, coeficiente);
        } catch (BadRequestException e) {
            return false;
        }
        audit.logAs(
                "rpa",
                "contrato_valor",
                String.valueOf(contratoId),
                "AJUSTE",
                "Registró ajuste automático por índice",
                nis,
                importeAnterior,
                importe.toPlainString()
        );
        return true;
    }

    private String insertarValorAjuste(
            long contratoId,
            LocalDate desde,
            BigDecimal importe,
            String origen,
            Integer indiceId,
            BigDecimal coeficiente) throws BadRequestException {
        Map<String, Object> vigente = repo.queryOne("""
            SELECT TOP 1 id, vigencia_desde AS desde, importe_mensual AS importe
              FROM contrato_valor
             WHERE contrato_id = :id AND vigencia_hasta IS NULL
             ORDER BY vigencia_desde DESC
            """, new MapSqlParameterSource("id", contratoId));

        String importeAnterior = null;
        if (vigente != null && vigente.get("desde") != null) {
            LocalDate desdeActual = AjusteIndiceCalculo.toLocalDate(vigente.get("desde"));
            if (desdeActual != null && !desde.isAfter(desdeActual)) {
                throw new BadRequestException("La fecha del ajuste debe ser posterior al valor vigente (" + desdeActual + ").");
            }
            if (vigente.get("importe") != null) importeAnterior = vigente.get("importe").toString();
            repo.jdbc().update("""
                UPDATE contrato_valor
                   SET vigencia_hasta = :hasta
                 WHERE id = :valorId
                """, new MapSqlParameterSource()
                    .addValue("hasta", desde.minusDays(1))
                    .addValue("valorId", vigente.get("id")));
        }

        repo.jdbc().update("""
            INSERT INTO contrato_valor
                (contrato_id, vigencia_desde, vigencia_hasta, importe_mensual, origen, indice_id, coeficiente_aplicado)
            VALUES
                (:contratoId, :desde, NULL, :importe, :origen, :indiceId, :coeficiente)
            """, new MapSqlParameterSource()
                .addValue("contratoId", contratoId)
                .addValue("desde", desde)
                .addValue("importe", importe)
                .addValue("origen", origen)
                .addValue("indiceId", indiceId)
                .addValue("coeficiente", coeficiente));
        return importeAnterior;
    }

    private static BigDecimal plainDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = o.toString().trim().replace(",", ".");
        if (s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private int estadoFromVencimiento(LocalDate venc) {
        LocalDate today = LocalDate.now();
        if (venc.isBefore(today)) return 3; // VENCIDO
        return 1;                           // VIGENTE
    }

    @Transactional
    public long createInmueble(Map<String, Object> body) throws BadRequestException {
        requireEdit();
        if (str(body.get("nis")) == null || str(body.get("nis")).isBlank()) {
            throw new BadRequestException("El NIS es obligatorio.");
        }
        if (str(body.get("denominacion")) == null || str(body.get("denominacion")).isBlank()) {
            throw new BadRequestException("La unidad de negocio es obligatoria.");
        }
        return insertInmueble(body);
    }

    @Transactional
    public long createIndice(Map<String, Object> body)
            throws BadRequestException {

        requireEdit();

        String codigo = str(body.get("codigo"));
        String nombre = str(body.get("nombre"));

        if (codigo == null || codigo.isBlank()) {
            throw new BadRequestException(
                "El código del índice es obligatorio."
            );
        }

        if (nombre == null || nombre.isBlank()) {
            throw new BadRequestException(
                "El nombre del índice es obligatorio."
            );
        }

        Integer existe = repo.jdbc().queryForObject(
            """
            SELECT COUNT(*)
            FROM indice_ajuste
            WHERE codigo = :codigo
            """,
            new MapSqlParameterSource()
                .addValue("codigo", codigo.trim()),
            Integer.class
        );

        if (existe != null && existe > 0) {
            throw new BadRequestException(
                "Ya existe un índice con el código " + codigo + "."
            );
        }

        return repo.insertIndice(body);
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Integer.parseInt(s);
    }

    private static BigDecimal asDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = o.toString().replaceAll("[^0-9,.-]", "").replace(".", "").replace(",", ".");
        if (s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate asDate(Object o) {
        if (o == null) return LocalDate.now();
        if (o instanceof LocalDate d) return d;
        String s = o.toString().trim();
        return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String blankToNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Long acreedorId(Map<String, Object> body, Long locadorId) throws BadRequestException {
        if (body.containsKey("acreedorSapCodigo")) {
            return resolveAcreedor(blankToNull(body.get("acreedorSapCodigo")), locadorId);
        }
        return asLong(body.get("acreedorSapId"));
    }

    private Long resolveAcreedor(String codigo, Long locadorId) throws BadRequestException {
        if (codigo == null) {
            return null;
        }
        List<Long> ids = repo.jdbc().query(
            "SELECT id FROM acreedor_sap WHERE codigo_sap = :codigo",
            new MapSqlParameterSource("codigo", codigo),
            (rs, rowNum) -> rs.getLong("id")
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        if (locadorId == null) {
            throw new BadRequestException("No se puede crear el acreedor SAP sin un locador.");
        }
        KeyHolder kh = new GeneratedKeyHolder();
        repo.jdbc().update("""
            INSERT INTO acreedor_sap (codigo_sap, locador_id, descripcion)
            VALUES (:codigo, :locadorId, :codigo)
            """,
            new MapSqlParameterSource()
                .addValue("codigo", codigo)
                .addValue("locadorId", locadorId),
            kh,
            new String[]{"id"}
        );
        return kh.getKey().longValue();
    }

private void validateCreateBody(Map<String, Object> body) throws BadRequestException {
    //requireValidLong(body, "acreedorSapId");
    requireValidInt(body, "tipoContratoId", "Tipo de Contrato");

    if (body.get("tipoFacturacion") == "mensual"){
        requireValidDate(body, "fechaInicio", "Fecha de Inicio");
        requireValidDate(body, "fechaVencimiento", "Fecha de Vencimiento");
    }

    body.put("localidadId", parseIntField(body, "localidadId", "Localidad")); 
    body.put("regionId", parseIntField(body, "regionId", "Región")); 
    if ("existente".equals(str(body.get("modoIndice")))){
        body.put("indiceId", parseIntField(body, "indiceId", "Indice de Ajuste"));
    }

    log.info("llega a pasar los int");
    
    requireValidString(body, "nis", "NIS");
    requireValidString(body, "denominacion", "Unidad de Negocio");
    requireValidString(body, "direccion", "Dirección");
    requireValidInt(body, "localidadId", "Localidad");
    requireValidInt(body, "regionId", "Región");

    requireValidDecimal(body, "importeTotal", "Importe Total");
    if ("existente".equals(str(body.get("modoIndice")))){
        requireValidInt(body, "indiceId", "Indice de Ajuste");
    }
    requireValidString(body, "periodicidad", "Frecuencia");
    //requireValidInt(body, "tipoComprobanteId");
    requireValidDecimal(body, "tolerancia", "Tolerancia de Diferencia");

    if (body.containsKey("inmuebleId")) {
        requireValidLong(body, "inmuebleId", "Inmueble");
    }

    if (body.containsKey("razonSocial")) {
        requireValidString(body, "razonSocial", "Razon Social");
    }

    if (body.containsKey("cuit")) {

        Object value = body.get("cuit");

        if (value == null) {
            throw new BadRequestException(
                "El campo CUIT no puede ser null."
            );
        }

        String cuit = String.valueOf(value).trim();

        if (cuit.isEmpty()) {
            throw new BadRequestException(
                "El campo CUIT no puede estar vacío."
            );
        }

        // Eliminar guiones si vienen en formato 30-12345678-9
        cuit = cuit.replace("-", "").replace(" ", "");

        // Verificar que solamente tenga números
        if (!cuit.matches("\\d+")) {
            throw new BadRequestException(
                "El campo CUIT debe contener solamente números."
            );
        }

        // Verificar exactamente 11 dígitos
        if (cuit.length() != 11) {
            throw new BadRequestException(
                "El campo CUIT debe tener exactamente 11 dígitos."
            );
        }

        try {
            Long cuitNumero = Long.valueOf(cuit);

            // Guardarlo convertido en el body
            body.put("cuit", cuitNumero);

        } catch (NumberFormatException e) {
            throw new BadRequestException(
                "El campo CUIT debe ser un número válido."
            );
        }
    }


    if (body.containsKey("locadorId")) {
        requireValidLong(body, "locadorId", "Locador");
    }

    if (body.get("tipoFacturacion") == "mensual"){
        LocalDate fechaInicio = parseDate(body.get("fechaInicio"), "fechaInicio", "Fecha de Inicio");
        LocalDate fechaVencimiento = parseDate(
            body.get("fechaVencimiento"),
            "fechaVencimiento",
            "Fecha de Vencimiento"
        );

        if (fechaVencimiento.isBefore(fechaInicio)) {
            throw new BadRequestException(
                "La fecha de vencimiento no puede ser anterior a la fecha de inicio."
            );
        }
    }

    Object facturasObj = body.get("facturas_planificadas");

    if (facturasObj == null) {
        throw new BadRequestException(
            "Las facturas son obligatorias."
        );
    }

    if (!(facturasObj instanceof List<?> facturas)) {
        throw new BadRequestException(
            "Las facturas deben ser una lista."
        );
    }

    if (facturas.isEmpty()) {
        throw new BadRequestException(
            "Debe existir al menos una factura."
        );
    }

    int totalPorcentaje = 0;

    for (int i = 0; i < facturas.size(); i++) {

        Object item = facturas.get(i);

        if (!(item instanceof Map<?, ?> factura)) {
            throw new BadRequestException(
                "La factura en la posición " + i +
                " no tiene un formato válido."
            );
        }

        String porcentajeField = "facturas[" + i + "].porcentaje";
        String importeField = "facturas[" + i + "].importe";

        requireValidInt(factura, "porcentaje", "Porcentaje de facturas");
        requireValidDecimal(factura, "importe", "Importe");

        Integer porcentaje = asInt(factura.get("porcentaje"));
        BigDecimal importe = asDecimal(factura.get("importe"));

        if (porcentaje < 0 || porcentaje > 100) {
            throw new BadRequestException(
                "El porcentaje de " + porcentajeField +
                " debe estar entre 0 y 100."
            );
        }

        if (importe.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(
                "El importe de " + importeField +
                " debe ser mayor o igual a 0."
            );
        }

        totalPorcentaje += porcentaje;
    }

    if (totalPorcentaje != 100) {
        throw new BadRequestException(
            "La suma de los porcentajes de las facturas debe ser 100%."
        );
    }

    log.info("hace todo");
}

private void requireValidString(
        Map<?, ?> body,
        String key,
        String nombre_campo) throws BadRequestException {

    if (!body.containsKey(key)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' es obligatorio."
        );
    }

    Object value = body.get(key);

    if (value == null) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede ser null."
        );
    }

    if (!(value instanceof String)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe ser texto."
        );
    }

    if (((String) value).isBlank()) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede estar vacío."
        );
    }
}

private void requireValidLong(
        Map<?, ?> body,
        String key,
        String nombre_campo) throws BadRequestException {

    if (!body.containsKey(key)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' es obligatorio."
        );
    }

    Object value = body.get(key);

    if (value == null) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede ser null."
        );
    }

    try {
        Long parsed = asLong(value);

        if (parsed == null) {
            throw new BadRequestException(
                "El campo '" + nombre_campo + "' no puede estar vacío."
            );
        }

        if (parsed <= 0) {
            throw new BadRequestException(
                "El campo '" + nombre_campo + "' debe ser mayor a 0."
            );
        }

    } catch (BadRequestException e) {
        throw e;
    } catch (Exception e) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe ser un número válido."
        );
    }
}

private void requireValidInt(
        Map<?, ?> body,
        String key,
        String nombre_campo) throws BadRequestException {

    if (!body.containsKey(key)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' es obligatorio."
        );
    }

    Object value = body.get(key);

    if (value == null) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede ser null."
        );
    }

    try {
        Integer parsed = asInt(value);

        if (parsed == null) {
            throw new BadRequestException(
                "El campo '" + nombre_campo + "' no puede estar vacío."
            );
        }

    } catch (BadRequestException e) {
        throw e;
    } catch (Exception e) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe ser un número entero válido."
        );
    }
}

private void requireValidDecimal(
        Map<?, ?> body,
        String key,
        String nombre_campo) throws BadRequestException {

    if (!body.containsKey(key)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' es obligatorio."
        );
    }

    Object value = body.get(key);

    if (value == null) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede ser null."
        );
    }

    try {
        BigDecimal parsed = asDecimal(value);

        if (parsed == null) {
            throw new BadRequestException(
                "El campo '" + nombre_campo + "' no puede estar vacío."
            );
        }

    } catch (BadRequestException e) {
        throw e;
    } catch (Exception e) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe ser un número válido."
        );
    }
}

private void requireValidDate(
        Map<?, ?> body,
        String key,
        String nombre_campo) throws BadRequestException {

    if (!body.containsKey(key)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' es obligatorio."
        );
    }

    Object value = body.get(key);

    if (value == null) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede ser null."
        );
    }

    if (!(value instanceof String)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe tener formato yyyy-MM-dd."
        );
    }

    String text = ((String) value).trim();

    if (text.isEmpty()) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede estar vacío."
        );
    }

    try {
        LocalDate.parse(text);
    } catch (DateTimeParseException e) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe tener una fecha válida con formato yyyy-MM-dd."
        );
    }
}

private LocalDate parseDate(
        Object value,
        String field,
        String nombre_campo) throws BadRequestException {

    if (!(value instanceof String text) || text.isBlank()) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe contener una fecha válida."
        );
    }

    try {
        return LocalDate.parse(text.trim());
    } catch (DateTimeParseException e) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' debe tener una fecha válida con formato yyyy-MM-dd."
        );
    }
}


private Integer parseIntField(
        Map<String, Object> body,
        String field,
        String nombre_campo) throws BadRequestException {

    if (!body.containsKey(field)) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' es obligatorio."
        );
    }

    Object value = body.get(field);

    if (value == null) {
        throw new BadRequestException(
            "El campo '" + nombre_campo + "' no puede ser null."
        );
    }

    if (value instanceof String s) {

        s = s.trim();

        if (s.isEmpty()) {
            throw new BadRequestException(
                "El campo '" + nombre_campo + "' no puede estar vacío."
            );
        }

        try {
            return Integer.valueOf(s);

        } catch (NumberFormatException e) {
            throw new BadRequestException(
                "El campo '" + nombre_campo + "' debe ser un número entero válido."
            );
        }
    }

    if (value instanceof Number number) {
        return number.intValue();
    }

    throw new BadRequestException(
        "El campo '" + nombre_campo + "' debe ser un número entero válido."
    );
}

private String normalizeAndValidateCuit(Object value)
        throws BadRequestException {

    if (value == null) {
        throw new BadRequestException(
            "El campo CUIT no puede ser null."
        );
    }

    String cuit = String.valueOf(value)
        .trim()
        .replace("-", "")
        .replace(" ", "");

    if (cuit.isEmpty()) {
        throw new BadRequestException(
            "El campo CUIT no puede estar vacío."
        );
    }

    if (!cuit.matches("\\d{11}")) {
        throw new BadRequestException(
            "El campo CUIT debe contener exactamente 11 dígitos."
        );
    }

    return cuit;
}

}
