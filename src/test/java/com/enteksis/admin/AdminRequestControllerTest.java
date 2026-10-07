package com.enteksis.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ADMIN_TOKEN=super-secret-admin-token-123"
})
class AdminRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Admin endpoint: Token başlığı eksikse 401 döner")
    void shouldReturn401_whenAuthHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", containsString("Yetkilendirme başlığı")));
    }

    @Test
    @DisplayName("Admin endpoint: Hatalı token ile 401 döner")
    void shouldReturn401_whenTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", containsString("Geçersiz yönetici anahtarı")));
    }

    @Test
    @DisplayName("Admin endpoint: Doğru token ile 200, maskeli e-posta ve Cache-Control: no-store döner")
    void shouldReturn200WithMaskedEmails_whenTokenIsValid() throws Exception {
        // Test verisi ekleyelim
        jdbcTemplate.update("""
            INSERT INTO service_requests (name, email, service, message)
            VALUES (?, ?, ?, ?)
            """,
                "Mehmet Demir",
                "mehmet.demir@kurumsal.com",
                "gorev-otomasyonu",
                "Yönetim paneli entegrasyonu için teklif istiyoruz."
        );

        mockMvc.perform(get("/api/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer super-secret-admin-token-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", is("no-store")))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].email", containsString("***@kurumsal.com")))
                .andExpect(jsonPath("$[0].email", not(containsString("mehmet.demir@kurumsal.com")))); // Ham e-posta sızdırılmaz
    }
}
