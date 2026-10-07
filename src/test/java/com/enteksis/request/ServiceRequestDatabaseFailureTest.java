package com.enteksis.request;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ServiceRequestController.class)
class ServiceRequestDatabaseFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceRequestRepository repository;

    @Test
    @DisplayName("Gereksinim 7: DB erişilemezken 500 döner, iç hata detayı/SQL sızdırılmaz")
    void shouldReturn500_withoutLeakingDetails_whenDbFails() throws Exception {
        when(repository.save(any()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused to database host"));

        Map<String, Object> body = Map.of(
                "name", "Ahmet Kaya",
                "email", "ahmet@enteksis.test",
                "service", "gorev-otomasyonu",
                "message", "Süreçlerimizi otomatikleştirmek için teklif istiyoruz."
        );

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", containsString("Talebiniz kaydedilirken bir hata oluştu.")))
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(containsString("Connection refused"))))
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(containsString("SQL"))));
    }
}
