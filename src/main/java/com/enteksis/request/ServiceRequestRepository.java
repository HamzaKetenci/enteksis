package com.enteksis.request;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServiceRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID save(ServiceRequestDto dto) {
        String sql = """
            INSERT INTO service_requests (name, email, service, message)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;
        // Parametreli sorgu: SQL Injection'a karşı tam koruma.
        return jdbcTemplate.queryForObject(
                sql,
                UUID.class,
                dto.name(),
                dto.email(),
                dto.service(),
                dto.message()
        );
    }
}
