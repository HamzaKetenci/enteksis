package com.enteksis.request;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        // isEmailValid ve isServiceValid gibi custom @AssertTrue metotları için alan adı eşleştirmesi
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            String code = error.getCode();
            if ("isEmailValid".equalsIgnoreCase(code) || "emailValid".equalsIgnoreCase(code)) {
                errors.putIfAbsent("email", error.getDefaultMessage());
            } else if ("isServiceValid".equalsIgnoreCase(code) || "serviceValid".equalsIgnoreCase(code)) {
                errors.putIfAbsent("service", error.getDefaultMessage());
            } else {
                errors.putIfAbsent("global", error.getDefaultMessage());
            }
        });

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("errors", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableException(HttpMessageNotReadableException ex) {
        // Bilinmeyen alanlar veya bozuk JSON formatı
        log.warn("Okunamayan istek gövdesi: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Geçersiz istek formatı veya tanınmayan alanlar mevcut."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseException(DataAccessException ex) {
        // İç SQL, DB veya stack trace detayları kullanıcıya asla sızdırılmaz
        log.error("Veritabanı işlemi sırasında hata oluştu: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Talebiniz kaydedilirken bir hata oluştu. Lütfen daha sonra tekrar deneyiniz."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Beklenmeyen hata: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Bir hata oluştu, lütfen daha sonra tekrar deneyiniz."));
    }
}
