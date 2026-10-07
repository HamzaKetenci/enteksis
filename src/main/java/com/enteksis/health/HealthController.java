package com.enteksis.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");

        try {
            // Bağlantı kontrolü
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // Tablo varlık kontrolü
            Boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS ("
                    + "SELECT 1 FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name = 'service_requests'"
                    + ")",
                    Boolean.class);

            if (Boolean.TRUE.equals(tableExists)) {
                result.put("database", "UP");
            } else {
                result.put("database", "DEGRADED");
                result.put("detail", "service_requests tablosu bulunamadı. "
                        + "Flyway migration çalışmamış olabilir.");
            }
        } catch (Exception e) {
            result.put("database", "DOWN");
            return ResponseEntity.status(503).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
