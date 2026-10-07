package com.enteksis.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * DB erişilemezse migration başarısız olur ama uygulama ayakta kalır.
     * /health endpoint'i DB ve tablo durumunu ayrıca raporlar.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            try {
                flyway.migrate();
                log.info("Flyway migration başarıyla tamamlandı.");
            } catch (Exception e) {
                log.error("Flyway migration başarısız: {}. "
                        + "Uygulama çalışmaya devam edecek ancak DB işlemleri başarısız olacak.", e.getMessage());
            }
        };
    }
}
