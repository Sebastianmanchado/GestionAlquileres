package com.correoargentino.sga.service;


import com.correoargentino.sga.web.NotificationController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.coyote.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService service;

    // ---------- POST /api/notifications ----------

    @Test
    @DisplayName("POST devuelve el id generado por el service")
    void create_ok() throws Exception {
        when(service.create(any())).thenReturn(42L);

        String body = """
            { "texto": "Vence el contrato", "tiempo": "10m" }
            """;

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("POST pasa el body tal cual al service")
    void create_pasaBodyAlService() throws Exception {
        when(service.create(any())).thenReturn(1L);

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"  hola  \",\"tiempo\":\"5m\",\"extra\":123}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).create(captor.capture());

        Map<String, Object> enviado = captor.getValue();
        assertThat(enviado).containsEntry("texto", "  hola  ");
        assertThat(enviado).containsEntry("tiempo", "5m");
        assertThat(enviado).containsEntry("extra", 123);
    }

    @Test
    @DisplayName("POST acepta tiempo nulo / ausente")
    void create_sinTiempo() throws Exception {
        when(service.create(any())).thenReturn(7L);

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"solo texto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("POST propaga BadRequestException cuando falta 'texto'")
    void create_textoFaltante() throws Exception {
        when(service.create(any()))
                .thenThrow(new BadRequestException("El campo 'texto' es obligatorio."));

        assertThatThrownBy(() ->
                mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tiempo\":\"10m\"}")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    @DisplayName("POST con JSON inválido devuelve 400 y no llama al service")
    void create_jsonInvalido() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json }"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST sin body devuelve 400")
    void create_sinBody() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST sin Content-Type JSON devuelve 415")
    void create_contentTypeInvalido() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto=hola"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(service);
    }

    // ---------- GET /api/notifications ----------

    @Test
    @DisplayName("GET devuelve la lista de notificaciones")
    void get_ok() throws Exception {
        Map<String, Object> n1 = new LinkedHashMap<>();
        n1.put("id", 2L);
        n1.put("texto", "Segunda");
        n1.put("tiempo", "1h");
        n1.put("creadoEn", "2026-09-28T10:00:00");

        Map<String, Object> n2 = new LinkedHashMap<>();
        n2.put("id", 1L);
        n2.put("texto", "Primera");
        n2.put("tiempo", null);
        n2.put("creadoEn", "2026-09-27T09:00:00");

        when(service.get()).thenReturn(List.of(n1, n2));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].id").value(2))
                .andExpect(jsonPath("$.notifications[0].texto").value("Segunda"))
                .andExpect(jsonPath("$.notifications[0].tiempo").value("1h"))
                .andExpect(jsonPath("$.notifications[0].creadoEn").value("2026-09-28T10:00:00"))
                .andExpect(jsonPath("$.notifications[1].id").value(1))
                .andExpect(jsonPath("$.notifications[1].tiempo").doesNotExist());

        verify(service).get();
    }

    @Test
    @DisplayName("GET devuelve array vacío si no hay notificaciones")
    void get_vacio() throws Exception {
        when(service.get()).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isArray())
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    // ---------- DELETE /api/notifications/{id} ----------

    @Test
    @DisplayName("DELETE existente devuelve 200 sin cuerpo")
    void delete_ok() throws Exception {
        doNothing().when(service).delete(5L);

        mockMvc.perform(delete("/api/notifications/{id}", 5))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(service).delete(5L);
    }

    @Test
    @DisplayName("DELETE inexistente propaga BadRequestException")
    void delete_noEncontrada() throws Exception {
        doThrow(new BadRequestException("No se encontró la notificación."))
                .when(service).delete(99L);

        assertThatThrownBy(() ->
                mockMvc.perform(delete("/api/notifications/{id}", 99)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se encontró la notificación.");
    }

    @Test
    @DisplayName("DELETE con id no numérico devuelve 400")
    void delete_idInvalido() throws Exception {
        mockMvc.perform(delete("/api/notifications/{id}", "abc"))
                .andExpect(status().isBadRequest());

        verify(service, never()).delete(anyLong());
    }
}