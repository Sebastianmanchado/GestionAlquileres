package com.correoargentino.sga.service;

import com.correoargentino.sga.repo.SgaRepository;
import com.correoargentino.sga.web.ForbiddenException;
import com.correoargentino.sga.web.NotFoundException;
import com.correoargentino.sga.web.ContractController;

import jakarta.servlet.ServletException;
import org.apache.coyote.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContractController.class)
class ContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private SgaRepository repo;
    @MockBean private ContractService service;
    @MockBean private AjusteAutomaticoService ajustes;

    private static final long ID = 100L;

    private static final String BODY_CONTRATO = """
        {
          "tipoContratoId": 1,
          "nis": "NIS-001",
          "denominacion": "Sucursal Centro",
          "direccion": "Av. Siempre Viva 123",
          "localidadId": 1,
          "regionId": 2,
          "modoIndice": "existente",
          "indiceId": 3,
          "importeTotal": 100000,
          "periodicidad": "TRIMESTRAL",
          "tolerancia": 3,
          "locadorId": 66,
          "facturas_planificadas": [
            { "porcentaje": 60, "importe": 60000 },
            { "porcentaje": 40, "importe": 40000 }
          ]
        }
        """;

    private static final String BODY_AJUSTE = """
        { "origen": "ACUERDO", "importe": 120000, "desde": "2026-10-01" }
        """;

    // =====================================================
    // Helpers
    // =====================================================

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    /**
     * Para endpoints SIN try/catch (get y delete).
     * Si la excepción tiene @ResponseStatus o hay un @RestControllerAdvice, se espera el status;
     * si no, MockMvc la propaga envuelta en ServletException y se verifica el tipo.
     */
    private void expectNoManejada(MockHttpServletRequestBuilder request,
                                  ResultMatcher statusSiHayHandler,
                                  Class<? extends Throwable> tipo) throws Exception {
        try {
            mockMvc.perform(request).andExpect(statusSiHayHandler);
        } catch (ServletException e) {
            assertThat(e).hasRootCauseInstanceOf(tipo);
        }
    }

    private MockHttpServletRequestBuilder postContrato() {
        return post("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY_CONTRATO);
    }

    private MockHttpServletRequestBuilder putContrato(Object id) {
        return put("/api/contracts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY_CONTRATO);
    }

    private MockHttpServletRequestBuilder postAjuste(Object id) {
        return post("/api/contracts/{id}/ajustes", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY_AJUSTE);
    }

    // =====================================================
    // GET /api/contracts
    // =====================================================

    @Nested
    class Listar {

        @Test
        @DisplayName("sin parámetros usa los valores por defecto (page=0, size=14)")
        void defaults() throws Exception {
            when(repo.listContracts(any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(Map.of("content", List.of(Map.of("id", 1)), "total", 1));

            mockMvc.perform(get("/api/contracts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(1));

            verify(repo, times(1)).listContracts(null, null, null, null, null, 0, 14, null, null);
            verifyNoMoreInteractions(repo);
            verifyNoInteractions(service, ajustes);
        }

        @Test
        @DisplayName("pasa todos los filtros al repositorio")
        void conFiltros() throws Exception {
            when(repo.listContracts(any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(Map.of("content", List.of(), "total", 0));

            mockMvc.perform(get("/api/contracts")
                            .param("search", "Centro")
                            .param("region", "AMBA")
                            .param("estado", "VIGENTE")
                            .param("indice", "ICL")
                            .param("venc", "30")
                            .param("page", "2")
                            .param("size", "20")
                            .param("sort", "nis")
                            .param("dir", "desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            verify(repo, times(1)).listContracts(
                    "Centro", "AMBA", "VIGENTE", "ICL", "30", 2, 20, "nis", "desc");
            verifyNoMoreInteractions(repo);
        }

        @Test
        @DisplayName("page no numérico devuelve 400 y no consulta")
        void pageInvalido() throws Exception {
            mockMvc.perform(get("/api/contracts").param("page", "abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(repo, service, ajustes);
        }
    }

    // =====================================================
    // GET /api/contracts/{id}
    // =====================================================

    @Nested
    class Obtener {

        @Test
        @DisplayName("devuelve el detalle con el próximo ajuste")
        void ok() throws Exception {
            Map<String, Object> detalle = new HashMap<>();
            detalle.put("id", ID);
            detalle.put("nis", "NIS-001");
            when(repo.getContractDetail(ID)).thenReturn(detalle);

            mockMvc.perform(get("/api/contracts/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID))
                    .andExpect(jsonPath("$.nis").value("NIS-001"))
                    .andExpect(jsonPath("$", hasKey("proximoAjuste")));

            verify(repo, times(1)).getContractDetail(ID);
            verify(ajustes, times(1)).proximoAjuste(ID);
            verifyNoMoreInteractions(repo, ajustes);
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("contrato inexistente: NotFoundException y no calcula el ajuste")
        void noExiste() throws Exception {
            when(repo.getContractDetail(999L)).thenReturn(null);

            expectNoManejada(get("/api/contracts/{id}", 999),
                    status().isNotFound(), NotFoundException.class);

            verify(ajustes, never()).proximoAjuste(anyLong());
        }

        @Test
        @DisplayName("id no numérico devuelve 400")
        void idInvalido() throws Exception {
            mockMvc.perform(get("/api/contracts/{id}", "abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(repo, ajustes);
        }
    }

    // =====================================================
    // POST /api/contracts
    // =====================================================

    @Nested
    class Crear {

        @Test
        @DisplayName("crea el contrato UNA sola vez y devuelve el id")
        void ok() throws Exception {
            when(service.create(any())).thenReturn(ID);

            mockMvc.perform(postContrato())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID))
                    .andExpect(jsonPath("$.length()").value(1));

            verify(service, times(1)).create(any());
            verifyNoMoreInteractions(service);
            verifyNoInteractions(repo, ajustes);
        }

        @Test
        @DisplayName("pasa el body íntegro al service en una sola invocación")
        void pasaBody() throws Exception {
            when(service.create(any())).thenReturn(ID);

            mockMvc.perform(postContrato()).andExpect(status().isOk());

            ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
            verify(service, times(1)).create(captor.capture());

            assertThat(captor.getAllValues()).hasSize(1);
            assertThat(captor.getValue())
                    .containsEntry("nis", "NIS-001")
                    .containsEntry("locadorId", 66)
                    .containsEntry("modoIndice", "existente");
            assertThat((List<?>) captor.getValue().get("facturas_planificadas")).hasSize(2);
        }

        @Test
        @DisplayName("cada request crea exactamente un contrato")
        void unaRequestUnContrato() throws Exception {
            when(service.create(any())).thenReturn(100L, 101L);

            mockMvc.perform(postContrato()).andExpect(jsonPath("$.id").value(100));
            verify(service, times(1)).create(any());

            mockMvc.perform(postContrato()).andExpect(jsonPath("$.id").value(101));
            verify(service, times(2)).create(any());

            verifyNoMoreInteractions(service);
        }

        @Test
        @DisplayName("BadRequestException devuelve 400 con el mensaje")
        void badRequest() throws Exception {
            when(service.create(any()))
                    .thenThrow(new BadRequestException("La suma de los porcentajes de las facturas debe ser 100%."));

            mockMvc.perform(postContrato())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("La suma de los porcentajes de las facturas debe ser 100%."))
                    .andExpect(jsonPath("$.id").doesNotExist());

            verify(service, times(1)).create(any());
        }

        @Test
        @DisplayName("NotFoundException cae en el catch genérico: 500 (create no maneja 404)")
        void notFoundEs500() throws Exception {
            when(service.create(any())).thenThrow(new NotFoundException("Contrato no encontrado"));

            mockMvc.perform(postContrato())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }

        @Test
        @DisplayName("ForbiddenException cae en el catch genérico: 500")
        void forbiddenEs500() throws Exception {
            when(service.create(any()))
                    .thenThrow(new ForbiddenException("El rol actual no tiene permiso para modificar contratos."));

            mockMvc.perform(postContrato())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Ocurrió un error interno."));
        }

        @Test
        @DisplayName("error inesperado: 500 sin filtrar el detalle interno")
        void errorInterno() throws Exception {
            when(service.create(any())).thenThrow(new RuntimeException("violación de fk_contrato_acreedor"));

            mockMvc.perform(postContrato())
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("Ocurrió un error interno."))
                    .andExpect(jsonPath("$.message", not(containsString("fk_contrato_acreedor"))));
        }

        @Test
        @DisplayName("JSON inválido: 400 y NO crea nada")
        void jsonInvalido() throws Exception {
            mockMvc.perform(post("/api/contracts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ roto }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service, repo, ajustes);
        }

        @Test
        @DisplayName("sin body: 400 y NO crea nada")
        void sinBody() throws Exception {
            mockMvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("Content-Type incorrecto: 415 y NO crea nada")
        void contentTypeInvalido() throws Exception {
            mockMvc.perform(post("/api/contracts")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("nis=NIS-001"))
                    .andExpect(status().isUnsupportedMediaType());

            verifyNoInteractions(service);
        }
    }

    // =====================================================
    // PUT /api/contracts/{id}
    // =====================================================

    @Nested
    class Actualizar {

        @Test
        @DisplayName("actualiza UNA sola vez y devuelve id + updated")
        void ok() throws Exception {
            doNothing().when(service).update(eq(ID), any());

            mockMvc.perform(putContrato(ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID))
                    .andExpect(jsonPath("$.updated").value(true))
                    .andExpect(jsonPath("$.length()").value(2));

            verify(service, times(1)).update(eq(ID), any());
            verifyNoMoreInteractions(service);
            verifyNoInteractions(repo, ajustes);
        }

        @Test
        @DisplayName("pasa id y body correctos al service")
        void pasaArgumentos() throws Exception {
            mockMvc.perform(putContrato(42)).andExpect(status().isOk());

            ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Map<String, Object>> bodyCaptor = mapCaptor();
            verify(service, times(1)).update(idCaptor.capture(), bodyCaptor.capture());

            assertThat(idCaptor.getValue()).isEqualTo(42L);
            assertThat(bodyCaptor.getValue()).containsEntry("nis", "NIS-001");
        }

        @Test
        @DisplayName("BadRequestException devuelve 400")
        void badRequest() throws Exception {
            doThrow(new BadRequestException("El campo 'NIS' no puede estar vacío."))
                    .when(service).update(eq(ID), any());

            mockMvc.perform(putContrato(ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("El campo 'NIS' no puede estar vacío."))
                    .andExpect(jsonPath("$.updated").doesNotExist());
        }

        @Test
        @DisplayName("NotFoundException devuelve 404")
        void notFound() throws Exception {
            doThrow(new NotFoundException("Contrato no encontrado"))
                    .when(service).update(eq(999L), any());

            mockMvc.perform(putContrato(999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Contrato no encontrado"));
        }

        @Test
        @DisplayName("ForbiddenException cae en el catch genérico: 500")
        void forbiddenEs500() throws Exception {
            doThrow(new ForbiddenException("sin permiso")).when(service).update(eq(ID), any());

            mockMvc.perform(putContrato(ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }

        @Test
        @DisplayName("error inesperado: 500 genérico")
        void errorInterno() throws Exception {
            doThrow(new RuntimeException("deadlock")).when(service).update(eq(ID), any());

            mockMvc.perform(putContrato(ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Ocurrió un error interno."));
        }

        @Test
        @DisplayName("JSON inválido: 400 y NO actualiza")
        void jsonInvalido() throws Exception {
            mockMvc.perform(put("/api/contracts/{id}", ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ roto }"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).update(anyLong(), any());
        }

        @Test
        @DisplayName("id no numérico: 400 y NO actualiza")
        void idInvalido() throws Exception {
            mockMvc.perform(putContrato("abc"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).update(anyLong(), any());
        }
    }

    // =====================================================
    // POST /api/contracts/{id}/ajustes
    // =====================================================

    @Nested
    class RegistrarAjuste {

        @Test
        @DisplayName("registra UN ajuste y devuelve id + ajustado")
        void ok() throws Exception {
            mockMvc.perform(postAjuste(ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID))
                    .andExpect(jsonPath("$.ajustado").value(true))
                    .andExpect(jsonPath("$.length()").value(2));

            ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
            verify(service, times(1)).registrarAjuste(eq(ID), captor.capture());
            assertThat(captor.getValue())
                    .containsEntry("origen", "ACUERDO")
                    .containsEntry("desde", "2026-10-01");

            verifyNoMoreInteractions(service);
            verifyNoInteractions(repo, ajustes);
        }

        @Test
        @DisplayName("BadRequestException devuelve 400")
        void badRequest() throws Exception {
            doThrow(new BadRequestException("El coeficiente debe ser mayor a cero."))
                    .when(service).registrarAjuste(eq(ID), any());

            mockMvc.perform(postAjuste(ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("El coeficiente debe ser mayor a cero."))
                    .andExpect(jsonPath("$.ajustado").doesNotExist());
        }

        @Test
        @DisplayName("NotFoundException devuelve 404")
        void notFound() throws Exception {
            doThrow(new NotFoundException("Contrato no encontrado"))
                    .when(service).registrarAjuste(eq(999L), any());

            mockMvc.perform(postAjuste(999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Contrato no encontrado"));
        }

        @Test
        @DisplayName("error inesperado: 500 genérico")
        void errorInterno() throws Exception {
            doThrow(new RuntimeException("boom")).when(service).registrarAjuste(eq(ID), any());

            mockMvc.perform(postAjuste(ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocurrió un error interno."));
        }

        @Test
        @DisplayName("JSON inválido: 400 y NO registra")
        void jsonInvalido() throws Exception {
            mockMvc.perform(post("/api/contracts/{id}/ajustes", ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ roto }"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).registrarAjuste(anyLong(), any());
        }
    }

    // =====================================================
    // DELETE /api/contracts/{id}
    // =====================================================

    @Nested
    class Eliminar {

        @Test
        @DisplayName("da de baja UNA sola vez y devuelve id + deleted")
        void ok() throws Exception {
            mockMvc.perform(delete("/api/contracts/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ID))
                    .andExpect(jsonPath("$.deleted").value(true));

            verify(service, times(1)).softDelete(ID);
            verifyNoMoreInteractions(service);
            verifyNoInteractions(repo, ajustes);
        }

        @Test
        @DisplayName("contrato inexistente: NotFoundException (no hay try/catch)")
        void noExiste() throws Exception {
            doThrow(new NotFoundException("Contrato no encontrado")).when(service).softDelete(999L);

            expectNoManejada(delete("/api/contracts/{id}", 999),
                    status().isNotFound(), NotFoundException.class);

            verify(service, times(1)).softDelete(999L);
        }

        @Test
        @DisplayName("rol sin permiso: ForbiddenException (no hay try/catch)")
        void sinPermiso() throws Exception {
            doThrow(new ForbiddenException("sin permiso")).when(service).softDelete(ID);

            expectNoManejada(delete("/api/contracts/{id}", ID),
                    status().isForbidden(), ForbiddenException.class);
        }

        @Test
        @DisplayName("id no numérico: 400 y NO elimina")
        void idInvalido() throws Exception {
            mockMvc.perform(delete("/api/contracts/{id}", "abc"))
                    .andExpect(status().isBadRequest());

            verify(service, never()).softDelete(anyLong());
        }
    }

    // =====================================================
    // Métodos no soportados
    // =====================================================

    @Test
    @DisplayName("PATCH no está expuesto: 405")
    void metodoNoSoportado() throws Exception {
        mockMvc.perform(patch("/api/contracts/{id}", ID))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service, repo, ajustes);
    }
}