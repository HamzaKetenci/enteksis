package com.enteksis.request;

import java.util.Map;
import com.enteksis.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceRequestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RateLimitFilter.reset();
    }

    @Test
    @DisplayName("Gereksinim 1: Geçerli kayıt 201 döner ve DB'de satır oluşturur")
    void shouldReturn201AndPersistRecord() throws Exception {
        int initialCount = jdbcTemplate.queryForObject("SELECT count(*) FROM service_requests", Integer.class);

        Map<String, Object> body = Map.of(
                "name", "Ahmet Kaya",
                "email", "ahmet@enteksis.test",
                "service", "gorev-otomasyonu",
                "message", "Süreçlerimizi otomatikleştirmek için teklif istiyoruz."
        );

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Talebiniz başarıyla alındı.")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));

        int afterCount = jdbcTemplate.queryForObject("SELECT count(*) FROM service_requests", Integer.class);
        assertEquals(initialCount + 1, afterCount);
    }

    @Test
    @DisplayName("Gereksinim 2-5: Geçersiz alanlar için 422 ve Türkçe hata mesajları")
    void shouldReject_withValidationErrors() throws Exception {
        Map<String, Object> invalidBody = Map.of(
                "name", "A", // Kısa
                "email", "gecersiz-eposta", // Domain noktasız
                "service", "tanimsiz-hizmet",
                "message", "Kısa" // <10 karakter
        );

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.service").exists())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test
    @DisplayName("Gereksinim 6: Honeypot (contactValidation) doluysa 422 döner ve DB'ye kayıt yazmaz")
    void shouldReject422_whenHoneypotFilled_andNotPersist() throws Exception {
        int initialCount = jdbcTemplate.queryForObject("SELECT count(*) FROM service_requests", Integer.class);

        Map<String, Object> botBody = Map.of(
                "name", "Bot User",
                "email", "bot@spammer.org",
                "service", "diger",
                "message", "Spam reklam içerikli mesaj metni en az on karakter.",
                "contactValidation", "http://spam-link.test" // Botun doldurduğu gizli alan
        );

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(botBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("Güvenlik doğrulaması başarısız oldu.")));

        int afterCount = jdbcTemplate.queryForObject("SELECT count(*) FROM service_requests", Integer.class);
        assertEquals(initialCount, afterCount);
    }

    @Test
    @DisplayName("Gereksinim 8: IP başına dakikada 5 istek aşılınca 429 Too Many Requests")
    void shouldReturn429_whenRateLimitExceeded() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Ahmet Kaya",
                "email", "ahmet@enteksis.test",
                "service", "entegrasyon",
                "message", "Entegrasyon hizmeti için bilgi almak istiyorum."
        );
        String json = objectMapper.writeValueAsString(body);

        // İlk 5 istek kabul edilmeli
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }

        // 6. istek 429 dönmeli
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", containsString("Çok fazla istek")));
    }

    @Test
    @DisplayName("Gereksinim 9: Bilinmeyen alanlar 400 ile reddedilmeli (fail-on-unknown-properties)")
    void shouldReject_unknownFieldsWith400() throws Exception {
        String jsonWithExtraField = """
            {
                "name": "Ahmet Kaya",
                "email": "ahmet@enteksis.test",
                "service": "diger",
                "message": "Bu geçerli bir açıklamadır.",
                "unknownField": "hackerData"
            }
            """;

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithExtraField))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("tanınmayan alanlar")));
    }

    @Test
    @DisplayName("Gereksinim: İstek gövdesi 16KB sınırını aşınca 413 Payload Too Large")
    void shouldReturn413_whenPayloadTooLarge() throws Exception {
        String largeText = "A".repeat(17000);
        String largeJson = "{\"name\":\"Test\",\"email\":\"test@test.com\",\"service\":\"diger\",\"message\":\"" + largeText + "\"}";

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(largeJson))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error", containsString("16KB")));
    }

    @Test
    @DisplayName("Gereksinim 10: GET /health DB bağlantısını ve tablo varlığını raporlar")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.database", is("UP")));
    }
}
