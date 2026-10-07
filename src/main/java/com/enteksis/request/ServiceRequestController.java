package com.enteksis.request;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class ServiceRequestController {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestController.class);

    private final ServiceRequestRepository repository;

    public ServiceRequestController(ServiceRequestRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<?> createRequest(@Validated @RequestBody ServiceRequestDto dto) {
        // Honeypot kontrolü: contactValidation alanı doluysa bot tespit edildi
        if (dto.contactValidation() != null && !dto.contactValidation().isBlank()) {
            log.warn("Honeypot tetiklendi, istek reddediliyor.");
            // Botu yanıltmadan/sahte başarı dönmeden açık 422 hatası veriyoruz
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Güvenlik doğrulaması başarısız oldu."));
        }

        UUID savedId = repository.save(dto);
        // Gizlilik ilkesi: Loglara kişisel veri (e-posta veya mesaj) asla yazılmaz
        log.info("Yeni talep oluşturuldu, id: {}", savedId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Talebiniz başarıyla alındı."));
    }
}
