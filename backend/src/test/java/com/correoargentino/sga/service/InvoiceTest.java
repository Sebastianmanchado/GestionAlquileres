package com.correoargentino.sga.service;

import org.apache.coyote.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.security.Role;
import com.correoargentino.sga.utils.FacturaValidator;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    @Mock private SgaRepository repo;
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private AuditService audit;
    @Mock private CurrentUserProvider currentUser;
    @Mock private NotificationService notificationService;

    private InvoiceService service;

    /** Período siempre válido: mes anterior al actual. */
    private static final YearMonth PERIODO = YearMonth.from(LocalDate.now()).minusMonths(1);
    private static final LocalDate PERIODO_FECHA = PERIODO.atDay(1);
    private static final LocalDate EMISION = PERIODO_FECHA.plusDays(10);

    @BeforeEach
    void setUp() {
        when(repo.jdbc()).thenReturn(jdbc);
        when(currentUser.currentRole()).thenReturn(Role.ANALISTA);

        FacturaValidator validator = new FacturaValidator(repo);

        service = new InvoiceService(repo, audit, currentUser, notificationService, validator);
    }

    // =====================================================
    // Helpers
    // =====================================================

    private Map<String, Object> bodyValido() {
        Map<String, Object> b = new HashMap<>();
        b.put("cuit", "30-71234567-0");
        b.put("razonSocial", "Inmobiliaria SA");
        b.put("comprobante", "0001-00000123");
        b.put("importe", "121000,00");
        b.put("periodo", PERIODO.toString());
        b.put("fechaEmision", EMISION.toString());
        b.put("tipo", "A");
        b.put("puntoVenta", "1");
        b.put("moneda", "ARS");
        return b;
    }

    /** Stub del INSERT con KeyHolder: devuelve el id indicado. */
    private void stubInsertConId(long id) {
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(2);
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("id", id);
            kh.getKeyList().add(key);
            return 1;
        }).when(jdbc).update(
                contains("INSERT INTO factura"),
                any(MapSqlParameterSource.class),
                any(KeyHolder.class),
                any(String[].class));
    }

    /** Stub de tipo_comprobante → id 1. */
    private void stubTipoComprobante() {
        when(jdbc.query(contains("FROM tipo_comprobante"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(1));
    }

    /** Stub de la búsqueda de locador: devuelve los ids indicados. */
    private void stubLocador(List<Long> ids) {
        when(jdbc.query(contains("FROM locador"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(ids);
    }

    /** Stub de la búsqueda de contrato por locador+período. */
    private void stubContratosDelLocador(List<Long> ids) {
        when(jdbc.query(contains("FROM contrato"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(ids);
    }

    /** Stubs completos para que assign() corra hasta el final. */
    private void stubAssignFeliz(long facturaId, long contratoId, long conciliacionId,
                                 BigDecimal esperado, BigDecimal totalFacturado, int cantidad) {

        Map<String, Object> contrato = new LinkedHashMap<>();
        contrato.put("id", contratoId);
        contrato.put("inmuebleId", 500L);
        contrato.put("nis", "NIS-001");
        contrato.put("denom", "Sucursal Centro");

        Map<String, Object> factura = new LinkedHashMap<>();
        factura.put("id", facturaId);
        factura.put("contratoIdActual", null);
        factura.put("importeTotal", new BigDecimal("121000.00"));
        factura.put("periodoFacturado", PERIODO_FECHA);
        factura.put("numeroComprobante", "0001-00000123");

        Map<String, Object> conciliacion = new LinkedHashMap<>();
        conciliacion.put("id", conciliacionId);
        conciliacion.put("importeEsperado", esperado);

        when(repo.queryOne(contains("FROM contrato c"), any(MapSqlParameterSource.class)))
                .thenReturn(contrato);
        when(repo.queryOne(contains("FROM factura"), any(MapSqlParameterSource.class)))
                .thenReturn(factura);
        when(repo.queryOne(contains("FROM conciliacion"), any(MapSqlParameterSource.class)))
                .thenReturn(conciliacion);

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("cantidad", cantidad);
        resumen.put("totalFacturado", totalFacturado);

        when(jdbc.queryForMap(contains("FROM conciliacion_factura cf"), any(MapSqlParameterSource.class)))
                .thenReturn(resumen);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
    }

    // =====================================================
    // create() — no duplicación
    // =====================================================

    @Nested
    class Create {

        @Test
        @DisplayName("inserta la factura EXACTAMENTE una vez")
        void insertaUnaSolaVez() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            stubLocador(List.of());   // sin match → no assign

            long id = service.create(bodyValido());

            assertThat(id).isEqualTo(77L);

            // ---- clave: un solo INSERT INTO factura ----
            verify(jdbc, times(1)).update(
                    contains("INSERT INTO factura"),
                    any(MapSqlParameterSource.class),
                    any(KeyHolder.class),
                    any(String[].class));
        }

        @Test
        @DisplayName("sin match de locador: NO asigna, notifica una sola vez, no toca conciliacion_factura")
        void sinMatchLocador() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            stubLocador(List.of());

            service.create(bodyValido());

            // no se ejecutó nada del flujo de assign
            verify(jdbc, never()).update(contains("DELETE FROM conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, never()).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, never()).update(contains("UPDATE conciliacion"), any(MapSqlParameterSource.class));

            // exactamente una notificación: la de "no pudo ser asignada"
            ArgumentCaptor<Map<String, Object>> notiCaptor = mapCaptor();
            verify(notificationService, times(1)).create(notiCaptor.capture());
            assertThat(notiCaptor.getValue().get("texto").toString())
                    .contains("no pudo ser asignada automáticamente");

            verify(audit, times(1)).log(eq("factura"), eq("77"), eq("CREAR"),
                    anyString(), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("múltiples locadores con el mismo CUIT: NO asigna")
        void locadorAmbiguo() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            stubLocador(List.of(1L, 2L));

            service.create(bodyValido());

            verify(jdbc, never()).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(notificationService, times(1)).create(any());
        }

        @Test
        @DisplayName("múltiples contratos vigentes: NO asigna y notifica una vez")
        void contratoAmbiguo() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            stubLocador(List.of(1L));
            stubContratosDelLocador(List.of(10L, 11L));

            service.create(bodyValido());

            verify(jdbc, never()).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(notificationService, times(1)).create(any());
        }

        @Test
        @DisplayName("match único: asigna UNA vez — un solo INSERT en conciliacion_factura y un solo UPDATE de conciliación")
        void matchUnicoNoDuplica() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);

            // locador único y contrato único
            when(jdbc.query(contains("FROM locador"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of(1L));
            when(jdbc.query(contains("FROM contrato\nWHERE locador_id"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of(10L));

            stubAssignFeliz(77L, 10L, 900L,
                    new BigDecimal("121000.00"), new BigDecimal("121000.00"), 1);

            service.create(bodyValido());

            // ---- el corazón del anti-duplicación ----
            verify(jdbc, times(1)).update(contains("DELETE FROM conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).update(contains("UPDATE factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).update(contains("UPDATE conciliacion"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).queryForMap(contains("FROM conciliacion_factura cf"), any(MapSqlParameterSource.class));

            // una sola factura insertada, pese a haber pasado por assign
            verify(jdbc, times(1)).update(contains("INSERT INTO factura"),
                    any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        }

        @Test
        @DisplayName("match único: notifica SOLO la asignación, nunca las dos notificaciones")
        void matchUnicoUnaSolaNotificacion() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            when(jdbc.query(contains("FROM locador"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of(1L));
            when(jdbc.query(contains("FROM contrato\nWHERE locador_id"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of(10L));
            stubAssignFeliz(77L, 10L, 900L,
                    new BigDecimal("121000.00"), new BigDecimal("121000.00"), 1);

            service.create(bodyValido());

            ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
            verify(notificationService, times(1)).create(captor.capture());

            assertThat(captor.getAllValues()).hasSize(1);
            assertThat(captor.getValue().get("texto").toString())
                    .contains("se asignó al contrato")
                    .doesNotContain("no pudo ser asignada");
        }

        @Test
        @DisplayName("match único: audita CREAR y ASIGNAR una vez cada uno")
        void auditoriaSinDuplicados() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            when(jdbc.query(contains("FROM locador"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of(1L));
            when(jdbc.query(contains("FROM contrato\nWHERE locador_id"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of(10L));
            stubAssignFeliz(77L, 10L, 900L,
                    new BigDecimal("121000.00"), new BigDecimal("121000.00"), 1);

            service.create(bodyValido());

            verify(audit, times(1)).log(eq("factura"), eq("77"), eq("ASIGNAR"),
                    anyString(), anyString(), isNull(), isNull());
            verify(audit, times(1)).log(eq("factura"), eq("77"), eq("CREAR"),
                    anyString(), anyString(), isNull(), isNull());
            verify(audit, times(2)).log(anyString(), anyString(), anyString(),
                    anyString(), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("calcula neto e iva a partir del total (1.21)")
        void calculaNetoEIva() throws Exception {
            stubTipoComprobante();
            stubInsertConId(77L);
            stubLocador(List.of());

            ArgumentCaptor<MapSqlParameterSource> captor =
                    ArgumentCaptor.forClass(MapSqlParameterSource.class);

            service.create(bodyValido());

            verify(jdbc).update(contains("INSERT INTO factura"), captor.capture(),
                    any(KeyHolder.class), any(String[].class));

            MapSqlParameterSource p = captor.getValue();
            assertThat(p.getValue("total")).isEqualTo(new BigDecimal("121000.00"));
            assertThat(p.getValue("neto")).isEqualTo(new BigDecimal("100000.00"));
            assertThat(p.getValue("iva")).isEqualTo(new BigDecimal("21000.00"));
            assertThat(p.getValue("cuit")).isEqualTo("30712345670"); // guiones removidos
            assertThat(p.getValue("periodo")).isEqualTo(PERIODO_FECHA);
        }

        @Test
        @DisplayName("rol sin permiso: ForbiddenException y NINGÚN insert")
        void rolSinPermiso() {
            when(currentUser.currentRole()).thenReturn(Role.AUDITOR);

            assertThatThrownBy(() -> service.create(bodyValido()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no puede modificar facturas");

            verifyNoInteractions(notificationService);
            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class),
                    any(KeyHolder.class), any(String[].class));
        }

    // =====================================================
    // create() — validaciones: nada se inserta
    // =====================================================

    @Nested
    class Validaciones {

        @Test
        @DisplayName("importe negativo o cero rechaza y no inserta")
        void importeInvalido() {
            Map<String, Object> b = bodyValido();
            b.put("importe", "0");

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("mayor a 0");

            verifyNoInsert();
        }

        @Test
        @DisplayName("CUIT corto rechaza y no inserta")
        void cuitCorto() {
            Map<String, Object> b = bodyValido();
            b.put("cuit", "123");

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("11 caracteres");

            verifyNoInsert();
        }

        @Test
        @DisplayName("período futuro rechaza y no inserta")
        void periodoFuturo() {
            Map<String, Object> b = bodyValido();
            b.put("periodo", YearMonth.from(LocalDate.now()).plusMonths(1).toString());

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("mayor al mes actual");

            verifyNoInsert();
        }

        @Test
        @DisplayName("fecha de emisión futura rechaza y no inserta")
        void emisionFutura() {
            Map<String, Object> b = bodyValido();
            b.put("fechaEmision", LocalDate.now().plusDays(1).toString());

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("fecha actual");

            verifyNoInsert();
        }

        @Test
        @DisplayName("razón social vacía rechaza y no inserta")
        void razonSocialVacia() {
            Map<String, Object> b = bodyValido();
            b.put("razonSocial", "   ");

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Razón Social");

            verifyNoInsert();
        }

        @Test
        @DisplayName("tipo de comprobante inválido rechaza y no inserta")
        void tipoInvalido() {
            Map<String, Object> b = bodyValido();
            b.put("tipo", "Z");

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no es válido");

            verifyNoInsert();
        }

        @Test
        @DisplayName("moneda de longitud distinta de 3 rechaza")
        void monedaInvalida() {
            stubTipoComprobante();

            Map<String, Object> b = bodyValido();
            b.put("moneda", "PESOS");

            assertThatThrownBy(() -> service.create(b))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("3 caracteres");

            verifyNoInsert();
        }

        private void verifyNoInsert() {
            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class),
                    any(KeyHolder.class), any(String[].class));
            verifyNoInteractions(notificationService);
            verifyNoInteractions(audit);
        }
    }

    // =====================================================
    // assign() — el punto crítico de duplicación
    // =====================================================

    @Nested
    class Assign {

        @Test
        @DisplayName("un solo DELETE + un solo INSERT en conciliacion_factura")
        void sinDuplicarRelacion() throws Exception {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), new BigDecimal("100000.00"), 1);

            service.assign(1L, 10L);

            verify(jdbc, times(1)).update(contains("DELETE FROM conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).update(contains("UPDATE factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(1)).update(contains("UPDATE conciliacion"), any(MapSqlParameterSource.class));
            verify(notificationService, times(1)).create(any());
            verify(audit, times(1)).log(eq("factura"), eq("1"), eq("ASIGNAR"),
                    anyString(), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("reasignar la misma factura no acumula relaciones: DELETE previo por cada assign")
        void reasignacionLimpiaRelacionAnterior() throws Exception {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), new BigDecimal("100000.00"), 1);

            service.assign(1L, 10L);
            service.assign(1L, 10L);

            // 2 asignaciones → 2 DELETE y 2 INSERT, nunca 1 DELETE y 2 INSERT
            verify(jdbc, times(2)).update(contains("DELETE FROM conciliacion_factura"), any(MapSqlParameterSource.class));
            verify(jdbc, times(2)).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
        }

        @Test
        @DisplayName("estado OK cuando facturado == esperado")
        void estadoOk() throws Exception {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), new BigDecimal("100000.00"), 1);

            service.assign(1L, 10L);

            assertThat(estadoGuardado()).isEqualTo("OK");
        }

        @Test
        @DisplayName("estado OK_CON_DIF cuando el desvío entra en la tolerancia del 3%")
        void estadoOkConDif() throws Exception {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), new BigDecimal("102000.00"), 1);

            service.assign(1L, 10L);

            assertThat(estadoGuardado()).isEqualTo("OK_CON_DIF");
        }

        @Test
        @DisplayName("estado CON_DIFERENCIA cuando el desvío supera el 3%")
        void estadoConDiferencia() throws Exception {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), new BigDecimal("110000.00"), 1);

            service.assign(1L, 10L);

            assertThat(estadoGuardado()).isEqualTo("CON_DIFERENCIA");
        }

        @Test
        @DisplayName("estado SIN_FACTURA cuando la conciliación queda sin facturas")
        void estadoSinFactura() throws Exception {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), BigDecimal.ZERO, 0);

            service.assign(1L, 10L);

            assertThat(estadoGuardado()).isEqualTo("SIN_FACTURA");
        }

        @Test
        @DisplayName("contrato inexistente: NotFoundException y ningún update")
        void contratoInexistente() {
            when(repo.queryOne(contains("FROM contrato c"), any(MapSqlParameterSource.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.assign(1L, 999L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Contrato no encontrado");

            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("factura inexistente: NotFoundException y ningún update")
        void facturaInexistente() {
            Map<String, Object> contrato = new LinkedHashMap<>();
            contrato.put("id", 10L);
            contrato.put("inmuebleId", 500L);
            contrato.put("nis", "NIS-001");
            contrato.put("denom", "Sucursal");

            when(repo.queryOne(contains("FROM contrato c"), any(MapSqlParameterSource.class)))
                    .thenReturn(contrato);
            when(repo.queryOne(contains("FROM factura"), any(MapSqlParameterSource.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.assign(999L, 10L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Factura no encontrada");

            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
        }

        @Test
        @DisplayName("factura sin período: BadRequestException y ningún update")
        void sinPeriodo() {
            Map<String, Object> contrato = new LinkedHashMap<>();
            contrato.put("id", 10L);
            contrato.put("inmuebleId", 500L);
            contrato.put("nis", "NIS-001");
            contrato.put("denom", "Sucursal");

            Map<String, Object> factura = new LinkedHashMap<>();
            factura.put("id", 1L);
            factura.put("periodoFacturado", null);
            factura.put("numeroComprobante", "0001-1");

            when(repo.queryOne(contains("FROM contrato c"), any(MapSqlParameterSource.class))).thenReturn(contrato);
            when(repo.queryOne(contains("FROM factura"), any(MapSqlParameterSource.class))).thenReturn(factura);

            assertThatThrownBy(() -> service.assign(1L, 10L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("período facturado");

            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("sin conciliación del contrato: NotFoundException y ningún update")
        void sinConciliacion() {
            Map<String, Object> contrato = new LinkedHashMap<>();
            contrato.put("id", 10L);
            contrato.put("inmuebleId", 500L);
            contrato.put("nis", "NIS-001");
            contrato.put("denom", "Sucursal");

            Map<String, Object> factura = new LinkedHashMap<>();
            factura.put("id", 1L);
            factura.put("periodoFacturado", PERIODO_FECHA);
            factura.put("numeroComprobante", "0001-1");

            when(repo.queryOne(contains("FROM contrato c"), any(MapSqlParameterSource.class))).thenReturn(contrato);
            when(repo.queryOne(contains("FROM factura"), any(MapSqlParameterSource.class))).thenReturn(factura);
            when(repo.queryOne(contains("FROM conciliacion"), any(MapSqlParameterSource.class))).thenReturn(null);

            assertThatThrownBy(() -> service.assign(1L, 10L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("No existe una conciliación");

            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
        }

        @Test
        @DisplayName("si el UPDATE de factura no afecta filas: NotFoundException")
        void updateSinFilas() {
            stubAssignFeliz(1L, 10L, 900L,
                    new BigDecimal("100000.00"), new BigDecimal("100000.00"), 1);

            when(jdbc.update(contains("UPDATE factura"), any(MapSqlParameterSource.class))).thenReturn(0);

            assertThatThrownBy(() -> service.assign(1L, 10L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("No se pudo asignar");

            // no llegó a crear la relación ni a notificar
            verify(jdbc, never()).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("rol sin permiso: ForbiddenException y ningún update")
        void rolSinPermiso() {
            when(currentUser.currentRole()).thenReturn(Role.AUDITOR);

            assertThatThrownBy(() -> service.assign(1L, 10L))
                    .isInstanceOf(ForbiddenException.class);

            verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
        }

        private String estadoGuardado() {
            ArgumentCaptor<SqlParameterSource> captor =
                    ArgumentCaptor.forClass(MapSqlParameterSource.class);
            verify(jdbc).update(contains("UPDATE conciliacion"), captor.capture());
            return (String) captor.getValue().getValue("estado");
        }
    }

    // =====================================================
    // update()
    // =====================================================

    @Nested
    class Update {

        private void stubFacturaExistente() {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", 1L);
            f.put("cuit", "30712345670");
            when(repo.queryOne(contains("LEFT JOIN tipo_comprobante"), any(MapSqlParameterSource.class)))
                    .thenReturn(f);
        }

        @Test
        @DisplayName("un solo UPDATE de factura y ninguna notificación si no reasigna")
        void updateUnaSolaVez() throws Exception {
            stubFacturaExistente();
            stubTipoComprobante();
            stubLocador(List.of());
            when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

            service.update(1L, bodyValido());

            verify(jdbc, times(1)).update(contains("UPDATE factura"), any(MapSqlParameterSource.class));
            verify(jdbc, never()).update(contains("INSERT INTO conciliacion_factura"), any(MapSqlParameterSource.class));
            verifyNoInteractions(notificationService);
            verify(audit, times(1)).log(eq("factura"), eq("1"), eq("EDITAR"),
                    anyString(), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("nunca inserta una factura nueva al actualizar")
        void noInsertaAlActualizar() throws Exception {
            stubFacturaExistente();
            stubTipoComprobante();
            stubLocador(List.of());
            when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

            service.update(1L, bodyValido());

            verify(jdbc, never()).update(contains("INSERT INTO factura"),
                    any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        }

        @Test
        @DisplayName("factura inexistente: NotFoundException y ningún UPDATE")
        void facturaInexistente() {
            when(repo.queryOne(contains("LEFT JOIN tipo_comprobante"), any(MapSqlParameterSource.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.update(999L, bodyValido()))
                    .isInstanceOf(NotFoundException.class);

            verify(jdbc, never()).update(contains("UPDATE factura"), any(MapSqlParameterSource.class));
            verifyNoInteractions(audit);
        }

        @Test
        @DisplayName("rol sin permiso: ForbiddenException y ningún UPDATE")
        void rolSinPermiso() {
            when(currentUser.currentRole()).thenReturn(Role.AUDITOR);

            assertThatThrownBy(() -> service.update(1L, bodyValido()))
                    .isInstanceOf(ForbiddenException.class);

            verify(jdbc, never()).update(contains("UPDATE factura"), any(MapSqlParameterSource.class));
            verifyNoInteractions(audit);
        }
    }

    // =====================================================
    // Consultas de lectura
    // =====================================================

    @Test
    @DisplayName("planificadas consulta factura_planificada una sola vez")
    void planificadas() {
        Map<String, Object> fp = new LinkedHashMap<>();
        fp.put("id", 5L);
        fp.put("contrato_id", 10L);
        fp.put("porcentaje_esperado", 50);
        fp.put("monto_esperado", new BigDecimal("60500.00"));
        fp.put("estado", "PENDIENTE");

        when(repo.query(contains("FROM factura_planificada"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(fp));

        assertThat(service.planificadas()).hasSize(1);

        verify(repo, times(1)).query(contains("FROM factura_planificada"), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("unassigned agrega sugerencias por factura sin duplicar filas")
    void unassignedAgregaSugerencias() {
        Map<String, Object> factura = new HashMap<>();
        factura.put("id", 1L);
        factura.put("cuit", "30712345670");

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(factura);

        Map<String, Object> sug = new LinkedHashMap<>();
        sug.put("contratoId", 10L);
        sug.put("nis", "NIS-001");

        when(repo.query(contains("WHERE f.estado = 'SIN_ASIGNAR'"), any(MapSqlParameterSource.class)))
                .thenReturn(rows);
        when(repo.query(contains("WHERE lo.cuit = :cuit"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(sug));

        List<Map<String, Object>> result = service.unassigned();

        assertThat(result).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sugerencias =
                (List<Map<String, Object>>) result.get(0).get("sugerencias");
        assertThat(sugerencias).hasSize(1);

        // no cayó en el fallback
        verify(repo, never()).query(contains("WHERE e.codigo <> 'RESCINDIDO'\n                    ORDER BY"),
                any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("unassigned usa el fallback cuando no hay sugerencias por CUIT")
    void unassignedFallback() {
        Map<String, Object> factura = new HashMap<>();
        factura.put("id", 1L);
        factura.put("cuit", "30712345670");

        when(repo.query(contains("WHERE f.estado = 'SIN_ASIGNAR'"), any(MapSqlParameterSource.class)))
                .thenReturn(new ArrayList<>(List.of(factura)));
        when(repo.query(contains("WHERE lo.cuit = :cuit"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());
        when(repo.query(argThat(sql -> sql != null && sql.contains("e.codigo <> 'RESCINDIDO'")
                        && !sql.contains("lo.cuit = :cuit")), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("contratoId", 99L)));

        List<Map<String, Object>> result = service.unassigned();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sugerencias =
                (List<Map<String, Object>>) result.get(0).get("sugerencias");
        assertThat(sugerencias).hasSize(1);
    }

    @Test
    @DisplayName("get devuelve la factura")
    void getOk() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("id", 1L);
        when(repo.queryOne(contains("LEFT JOIN tipo_comprobante"), any(MapSqlParameterSource.class)))
                .thenReturn(f);

        assertThat(service.get(1L)).containsEntry("id", 1L);
    }

    @Test
    @DisplayName("get lanza NotFoundException si no existe")
    void getNoExiste() {
        when(repo.queryOne(anyString(), any(MapSqlParameterSource.class))).thenReturn(null);

        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Factura no encontrada");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
}