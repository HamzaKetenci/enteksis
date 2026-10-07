package com.enteksis.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceRequestDto(
        @NotBlank(message = "İsim alanı boş bırakılamaz.")
        @Size(min = 2, max = 80, message = "İsim 2 ile 80 karakter arasında olmalıdır.")
        String name,

        @NotBlank(message = "E-posta alanı boş bırakılamaz.")
        @Size(max = 254, message = "E-posta en fazla 254 karakter olabilir.")
        @Pattern(regexp = EMAIL_REGEX, message = "Geçerli bir e-posta adresi giriniz.")
        String email,

        @NotBlank(message = "Hizmet seçimi zorunludur.")
        @Pattern(regexp = "^(gorev-otomasyonu|rapor-otomasyonu|entegrasyon|diger)$", message = "Geçersiz hizmet türü seçildi.")
        String service,

        @NotBlank(message = "Açıklama alanı boş bırakılamaz.")
        @Size(min = 10, max = 1000, message = "Açıklama 10 ile 1000 karakter arasında olmalıdır.")
        String message,

        String contactValidation
) {
    // RFC 5322 uyumlu, alan adında nokta zorunlu ortak regex
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    public ServiceRequestDto(String name, String email, String service, String message, String contactValidation) {
        this.name = name != null ? name.trim() : null;
        this.email = email != null ? email.trim().toLowerCase() : null;
        this.service = service != null ? service.trim() : null;
        this.message = message != null ? message.trim() : null;
        this.contactValidation = contactValidation != null ? contactValidation.trim() : null;
    }
}
