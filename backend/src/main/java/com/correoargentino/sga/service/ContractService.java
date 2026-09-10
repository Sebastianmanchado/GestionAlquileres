package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ContractService {

    private final SgaRepository repo;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

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
    public long create(Map<String, Object> body) {
        requireEdit();

        Long inmuebleId = asLong(body.get("inmuebleId"));
        if (inmuebleId == null) {
            inmuebleId = insertInmueble(body);
        }
        Long locadorId = asLong(body.get("locadorId"));
        if (locadorId == null) {
            locadorId = insertLocador(body);
        }

        Integer tipoContrato = asInt(body.getOrDefault("tipoContratoId", 1));
        Integer indiceId = asInt(body.get("indiceId"));
        LocalDate inicio = asDate(body.getOrDefault("fechaInicio", LocalDate.now().toString()));
        LocalDate venc = asDate(body.getOrDefault("fechaVencimiento", LocalDate.now().plusYears(3).toString()));
        BigDecimal importe = asDecimal(body.getOrDefault("importeTotal", 0));
        BigDecimal deposito = asDecimal(body.get("deposito"));
        BigDecimal tolerancia = asDecimal(body.getOrDefault("tolerancia", 3));
        int estadoId = estadoFromVencimiento(venc);

        List<Map<String, Object>> facturas = (List<Map<String, Object>>) body.get("facturas");

        String numero = nextContractNumber();

        KeyHolder kh = new GeneratedKeyHolder();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("numero", numero)
                .addValue("inmuebleId", inmuebleId)
                .addValue("locadorId", locadorId)
                .addValue("acreedorId", asLong(body.get("acreedorSapId")))
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
                .addValue("obs", str(body.get("observaciones")))
                .addValue("cantidad_facturas", asInt(body.getOrDefault("cantidad_facturas", 1)));
        repo.jdbc().update("""
            INSERT INTO contrato (numero, inmueble_id, locador_id, acreedor_sap_id, tipo_contrato_id, estado_contrato_id,
                                  fecha_inicio, fecha_vencimiento, moneda, importe_inicial, deposito_garantia,
                                  indice_ajuste_id, periodicidad_ajuste, tipo_comprobante_id, tolerancia_importe_pct, observaciones, cantidad_facturas)
            VALUES (:numero, :inmuebleId, :locadorId, :acreedorId, :tipoContrato, :estadoId,
                    :inicio, :venc, 'ARS', :importe, :deposito, :indiceId, :periodicidad, :tipoComp, :tolerancia, :obs, :cantidad_facturas)
            """, p, kh, new String[]{"id"});
        long contratoId = kh.getKey().longValue();

        // Generacion de facturas planificadas
        if (facturas != null){
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
        }

        // valor vigente inicial
        repo.jdbc().update("""
            INSERT INTO contrato_valor (contrato_id, vigencia_desde, vigencia_hasta, importe_mensual, origen)
            VALUES (:c, :desde, NULL, :importe, 'CONTRATO')
            """, new MapSqlParameterSource().addValue("c", contratoId).addValue("desde", inicio).addValue("importe", importe));

        audit.log("contrato", String.valueOf(contratoId), "CREAR", "Creó el contrato " + numero,
                numero, null, null);
        return contratoId;
    }

    @Transactional
    public void update(long id, Map<String, Object> body) {
        requireEdit();
        Map<String, Object> before = repo.getContractDetail(id);
        if (before == null) throw new NotFoundException("Contrato no encontrado");

        LocalDate venc = asDate(body.getOrDefault("fechaVencimiento", String.valueOf(before.get("vencimiento"))));
        LocalDate inicio = asDate(body.getOrDefault("fechaInicio", String.valueOf(before.get("inicio"))));
        BigDecimal tolerancia = asDecimal(body.getOrDefault("tolerancia", before.get("tolerancia")));
        Integer indiceId = asInt(body.get("indiceId"));

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("inicio", inicio)
                .addValue("venc", venc)
                .addValue("estadoId", estadoFromVencimiento(venc))
                .addValue("indiceId", indiceId)
                .addValue("periodicidad", str(body.getOrDefault("periodicidad", before.get("periodicidad"))))
                .addValue("tolerancia", tolerancia)
                .addValue("obs", str(body.get("observaciones")));
        repo.jdbc().update("""
            UPDATE contrato SET fecha_inicio=:inicio, fecha_vencimiento=:venc, estado_contrato_id=:estadoId,
                   indice_ajuste_id = COALESCE(:indiceId, indice_ajuste_id),
                   periodicidad_ajuste=:periodicidad, tolerancia_importe_pct=:tolerancia,
                   observaciones = COALESCE(:obs, observaciones)
             WHERE id=:id
            """, p);

        // Ajuste de importe: cierra el valor vigente y abre uno nuevo
        BigDecimal nuevoImporte = body.get("importeMensual") == null ? null : asDecimal(body.get("importeMensual"));
        if (nuevoImporte != null) {
            BigDecimal actual = repo.jdbc().queryForObject(
                    "SELECT importe_mensual FROM contrato_valor WHERE contrato_id=:id AND vigencia_hasta IS NULL",
                    new MapSqlParameterSource("id", id), BigDecimal.class);
            if (actual == null || actual.compareTo(nuevoImporte) != 0) {
                repo.jdbc().update("UPDATE contrato_valor SET vigencia_hasta=CAST(GETDATE() AS DATE) WHERE contrato_id=:id AND vigencia_hasta IS NULL",
                        new MapSqlParameterSource("id", id));
                repo.jdbc().update("""
                    INSERT INTO contrato_valor (contrato_id, vigencia_desde, vigencia_hasta, importe_mensual, origen)
                    VALUES (:id, CAST(GETDATE() AS DATE), NULL, :importe, 'ACUERDO')
                    """, new MapSqlParameterSource().addValue("id", id).addValue("importe", nuevoImporte));
                audit.log("contrato", String.valueOf(id), "EDITAR", "Importe mensual",
                        String.valueOf(before.get("nis")), actual == null ? null : actual.toPlainString(), nuevoImporte.toPlainString());
            }
        }

        audit.log("contrato", String.valueOf(id), "EDITAR", "Actualizó datos del contrato",
                String.valueOf(before.get("nis")), null, null);
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

    private long insertLocador(Map<String, Object> body) {
        String cuit = str(body.getOrDefault("cuit", "00000000000"))
                .replace("-", "");
                
        Long locadorId = repo.jdbc().queryForObject("""
            SELECT id
            FROM locador
            WHERE cuit = :cuit
            """,
            new MapSqlParameterSource()
                .addValue("cuit", cuit),
            Long.class
        );

        if (locadorId != null) {

            repo.jdbc().update("""
                UPDATE locador
                SET email = :email,
                    telefono = :telefono
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                    .addValue("id", locadorId)
                    .addValue("email", str(body.get("email")))
                    .addValue("telefono", str(body.get("telefono")))
            );

            return locadorId;
        }

        KeyHolder kh = new GeneratedKeyHolder();

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("razon", str(body.getOrDefault("razonSocial", "Locador sin nombre")))
                .addValue("cuit", cuit)
                .addValue("email", str(body.get("email")))
                .addValue("telefono", str(body.get("telefono")));

        repo.jdbc().update("""
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
                :razon,
                :cuit,
                :email,
                :telefono,
                1
            )
            """,
            p,
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

    private int estadoFromVencimiento(LocalDate venc) {
        LocalDate today = LocalDate.now();
        if (venc.isBefore(today)) return 3;              // VENCIDO
        if (!venc.isAfter(today.plusDays(90))) return 2; // PROX_VENCER
        return 1;                                        // VIGENTE
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
}
