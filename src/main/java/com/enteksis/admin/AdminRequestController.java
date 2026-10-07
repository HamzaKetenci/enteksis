package com.enteksis.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class AdminRequestController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${ADMIN_TOKEN:#{null}}")
    private String adminToken;

    public AdminRequestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> listRequests(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        // Karar: ADMIN_TOKEN tanımlı değilse varsayılan token YOK, endpoint tamamen kapalıdır
        if (adminToken == null || adminToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Yönetici erişimi yapılandırılmamış veya devre dışı."));
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Yetkilendirme başlığı (Bearer token) eksik veya hatalı."));
        }

        String providedToken = authHeader.substring(7).trim();

        // Timing-attack koruması: MessageDigest.isEqual sabit zamanlı karşılaştırma yapar
        byte[] expectedBytes = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedToken.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Geçersiz yönetici anahtarı."));
        }

        // Karar: LIMIT 50 ORDER BY created_at DESC
        String sql = """
            SELECT id, name, email, service, message, created_at, status
            FROM service_requests
            ORDER BY created_at DESC
            LIMIT 50
            """;

        List<AdminRequestItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String rawEmail = rs.getString("email");
            Timestamp ts = rs.getTimestamp("created_at");
            OffsetDateTime createdAt = ts != null ? ts.toInstant().atOffset(ZoneOffset.UTC) : null;

            return new AdminRequestItem(
                    (UUID) rs.getObject("id"),
                    rs.getString("name"),
                    maskEmail(rawEmail), // KVKK & Gizlilik: E-posta maskeleme
                    rs.getString("service"),
                    rs.getString("message"),
                    createdAt,
                    rs.getString("status")
            );
        });

        // Güvenlik: Hassas admin verilerinin istemci veya aracı vekil sunucularda önbelleğe alınmaması
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(items);
    }

    /**
     * E-posta maskeleme yardımcısı.
     * Örn: "ali@ornek.com" -> "a***@ornek.com", "ahmet.kaya@sirket.com" -> "ah***@sirket.com"
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String namePart = parts[0];
        String domainPart = parts[1];

        if (namePart.length() <= 2) {
            return namePart.charAt(0) + "***@" + domainPart;
        } else {
            return namePart.substring(0, 2) + "***@" + domainPart;
        }
    }

    public record AdminRequestItem(
            UUID id,
            String name,
            String email,
            String service,
            String message,
            OffsetDateTime createdAt,
            String status
    ) {}
}
