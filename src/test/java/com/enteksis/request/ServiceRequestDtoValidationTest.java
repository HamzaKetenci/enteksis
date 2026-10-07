package com.enteksis.request;

import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Geçerli veri ile doğrulama başarılı olmalı")
    void validDto_shouldPassValidation() {
        ServiceRequestDto dto = new ServiceRequestDto(
                "Ali Yılmaz",
                "ali@example.com",
                "gorev-otomasyonu",
                "İşletmemiz için görev otomasyonu kurmak istiyoruz.",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Sadece boşluklardan oluşan isim ve mesaj reddedilmeli")
    void whitespaceOnly_shouldBeRejected() {
        ServiceRequestDto dto = new ServiceRequestDto(
                "    ",
                "ali@example.com",
                "gorev-otomasyonu",
                "          ",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        // Hem name hem message için @NotBlank veya @Size ihlali olmalı
        boolean hasNameError = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        boolean hasMessageError = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("message"));
        assertTrue(hasNameError);
        assertTrue(hasMessageError);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "", " "})
    @DisplayName("Kısa isimler (<2 karakter) reddedilmeli")
    void invalidShortName_shouldBeRejected(String name) {
        ServiceRequestDto dto = new ServiceRequestDto(
                name,
                "ali@example.com",
                "gorev-otomasyonu",
                "Bu geçerli uzunlukta bir açıklama mesajıdır.",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("80 karakterden uzun isim reddedilmeli")
    void tooLongName_shouldBeRejected() {
        String longName = "A".repeat(81);
        ServiceRequestDto dto = new ServiceRequestDto(
                longName,
                "ali@example.com",
                "gorev-otomasyonu",
                "Bu geçerli uzunlukta bir açıklama mesajıdır.",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", "user@", "user@localhost", "@domain.com", "user@domain"})
    @DisplayName("Geçersiz e-posta formatları (alan adında nokta olmayan dahil) reddedilmeli")
    void invalidEmail_shouldBeRejected(String email) {
        ServiceRequestDto dto = new ServiceRequestDto(
                "Ali Yılmaz",
                email,
                "gorev-otomasyonu",
                "Bu geçerli uzunlukta bir açıklama mesajıdır.",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gorev-otomasyonu", "rapor-otomasyonu", "entegrasyon", "diger"})
    @DisplayName("İzin verilen 4 hizmet türü başarıyla kabul edilmeli")
    void validServices_shouldBeAccepted(String service) {
        ServiceRequestDto dto = new ServiceRequestDto(
                "Ali Yılmaz",
                "ali@example.com",
                service,
                "Bu geçerli uzunlukta bir açıklama mesajıdır.",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Bilinmeyen hizmet türü reddedilmeli")
    void invalidService_shouldBeRejected() {
        ServiceRequestDto dto = new ServiceRequestDto(
                "Ali Yılmaz",
                "ali@example.com",
                "hacker-service",
                "Bu geçerli uzunlukta bir açıklama mesajıdır.",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("10 karakterden kısa açıklama reddedilmeli")
    void shortMessage_shouldBeRejected() {
        ServiceRequestDto dto = new ServiceRequestDto(
                "Ali Yılmaz",
                "ali@example.com",
                "gorev-otomasyonu",
                "Kısa",
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("1000 karakterden uzun açıklama reddedilmeli")
    void tooLongMessage_shouldBeRejected() {
        String longMessage = "M".repeat(1001);
        ServiceRequestDto dto = new ServiceRequestDto(
                "Ali Yılmaz",
                "ali@example.com",
                "gorev-otomasyonu",
                longMessage,
                null
        );

        Set<ConstraintViolation<ServiceRequestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }
}
